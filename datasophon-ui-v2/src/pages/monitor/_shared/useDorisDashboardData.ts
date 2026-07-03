/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { useEffect, useRef, useState } from 'react';
import type { PrometheusMatrix } from './charts/promql';
import { mergeNamedSeries, vectorToScalar } from './charts/promql';
import type { DorisPanelDescriptor } from './dorisService';
import {
  fetchDorisNodeCount,
  queryDorisInstant,
  queryDorisRange,
} from './dorisService';
import { TIME_RANGE_SECONDS } from './panelTypes';
import type { TimeSeriesPoint } from './types';
import { runWithConcurrencyLimit } from './useDashboardData';

export interface UseDorisDashboardDataParams {
  panelDescriptors: Record<string, DorisPanelDescriptor>;
  panelIds: string[];
  instance: string;
  job: string;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
  concurrency?: number;
}

export interface DorisDashboardData {
  instant: Record<string, number>;
  series: Record<string, TimeSeriesPoint[]>;
  loading: boolean;
  error?: string;
}

const EMPTY_MATRIX: PrometheusMatrix = { resultType: 'matrix', result: [] };

/**
 * 逐点相除两组 matrix 序列，按 metric labels 精确匹配。
 *
 * 用于客户端比值合成（如堆占比、错误率、磁盘占比）：
 * result[i] = numerator[i] / denominator[i] * scale
 *
 * 匹配策略：filter key 不会出现在 labels 中（后端只放入 WHERE，不放入 SELECT），
 * 因此可以直接用全量 labels 的 JSON 字符串作为 key 进行配对。
 */
function divideMatrixPointwise(
  numerator: PrometheusMatrix,
  denominator: PrometheusMatrix,
  scale: number,
): PrometheusMatrix {
  const denomMap = new Map<string, Array<[number, string]>>();
  for (const s of denominator.result) {
    const key = JSON.stringify(Object.entries(s.metric).sort());
    denomMap.set(key, s.values);
  }

  const result = numerator.result.flatMap((numSeries) => {
    const key = JSON.stringify(Object.entries(numSeries.metric).sort());
    const denomValues = denomMap.get(key);
    if (!denomValues) return [];

    const denomByTs = new Map<number, number>();
    for (const [ts, v] of denomValues) {
      denomByTs.set(ts, parseFloat(v));
    }

    const values: Array<[number, string]> = numSeries.values.map(([ts, v]) => {
      const d = denomByTs.get(ts) ?? 0;
      const ratio = d !== 0 ? (parseFloat(v) / d) * scale : 0;
      return [ts, ratio.toFixed(4)];
    });

    return [{ metric: numSeries.metric, values }];
  });

  return { resultType: 'matrix', result };
}

/**
 * 从 Doris OTel 表取数的监控看板 hook。
 *
 * 与 useDashboardData 并行——不改动已有 Prometheus 取数路径，
 * 只供改写为 Doris 描述符的看板（如 NexusMonitor / DorisMonitor）调用。
 *
 * 支持的描述符类型：
 * - instant: 快照值，支持 agg / scale / filters / filtersNe
 * - multi-range: 时序面板，每条查询支持 rate / table / filters / groupBy / denominatorMetric
 * - node-count: 调角色注册表 /nodes 接口，替代 PromQL count(up==1)
 */
export function useDorisDashboardData({
  panelDescriptors,
  panelIds,
  instance,
  job,
  timeRange,
  clusterId = 1,
  refreshKey,
  concurrency = 4,
}: UseDorisDashboardDataParams): DorisDashboardData {
  const [data, setData] = useState<DorisDashboardData>({
    instant: {},
    series: Object.fromEntries(panelIds.map((id) => [id, []])),
    loading: true,
  });

  // 用 ref 持有最新值，避免依赖数组含模块常量导致无限循环
  const descriptorsRef = useRef(panelDescriptors);
  descriptorsRef.current = panelDescriptors;

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      setData((prev) => ({ ...prev, loading: true }));
      try {
        const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
        const end = Math.floor(Date.now() / 1000);
        const start = end - rangeSeconds;
        const step = Math.max(15, Math.floor(rangeSeconds / 200));

        const _descriptors = descriptorsRef.current;

        const instantIds = panelIds.filter(
          (id) => _descriptors[id]?.type === 'instant',
        );
        const nodeCountIds = panelIds.filter(
          (id) => _descriptors[id]?.type === 'node-count',
        );
        const multiRangeIds = panelIds.filter(
          (id) => _descriptors[id]?.type === 'multi-range',
        );

        // ── instant 快照 ──────────────────────────────────────────────────────
        const instantTasks: Array<() => Promise<readonly [string, number]>> =
          instantIds.map((id) => async () => {
            const def = _descriptors[id];
            if (def.type !== 'instant') return [id, 0] as const;
            try {
              const res = await queryDorisInstant({
                metric: def.metric,
                agg: def.agg,
                scale: def.scale,
                instance,
                job,
                time: end,
                clusterId,
                table: def.table,
                filters: def.filters,
                filtersNe: def.filtersNe,
              });
              return [id, res?.data ? vectorToScalar(res.data) : 0] as const;
            } catch {
              return [id, 0] as const;
            }
          });

        // ── node-count（角色注册表） ───────────────────────────────────────────
        const nodeCountTasks: Array<() => Promise<readonly [string, number]>> =
          nodeCountIds.map((id) => async () => {
            const def = _descriptors[id];
            if (def.type !== 'node-count') return [id, 0] as const;
            try {
              const res = await fetchDorisNodeCount(def.roleName, clusterId);
              return [id, res?.data ?? 0] as const;
            } catch {
              return [id, 0] as const;
            }
          });

        // ── multi-range 时序（含比值合成） ────────────────────────────────────
        const multiRangeTasks: Array<
          () => Promise<readonly [string, TimeSeriesPoint[]]>
        > = multiRangeIds.map((id) => async () => {
          const def = _descriptors[id];
          if (def.type !== 'multi-range') return [id, []] as const;

          const parts = await Promise.all(
            def.queries.map(async (q) => {
              try {
                if (q.denominatorMetric) {
                  // 比值合成：并行取分子分母，客户端逐点相除
                  const [numRes, denomRes] = await Promise.all([
                    queryDorisRange({
                      metric: q.metric,
                      rateWindow: q.rate,
                      scale: 1,
                      instance,
                      job,
                      start,
                      end,
                      step,
                      clusterId,
                      table: q.table,
                      quantile: q.quantile,
                      filters: q.filters,
                      filtersNe: q.filtersNe,
                      groupBy: q.groupBy,
                    }),
                    queryDorisRange({
                      metric: q.denominatorMetric,
                      rateWindow: q.rate,
                      scale: 1,
                      instance,
                      job,
                      start,
                      end,
                      step,
                      clusterId,
                      table: q.table,
                      filters: q.denominatorFilters,
                      filtersNe: q.denominatorFiltersNe,
                      groupBy: q.groupBy,
                    }),
                  ]);
                  const numMatrix = numRes?.data ?? EMPTY_MATRIX;
                  const denomMatrix = denomRes?.data ?? EMPTY_MATRIX;
                  return {
                    label: q.label,
                    matrix: divideMatrixPointwise(
                      numMatrix,
                      denomMatrix,
                      q.scale ?? 1,
                    ),
                  };
                }

                // 普通单指标查询
                const res = await queryDorisRange({
                  metric: q.metric,
                  rateWindow: q.rate,
                  scale: q.scale,
                  instance,
                  job,
                  start,
                  end,
                  step,
                  clusterId,
                  table: q.table,
                  quantile: q.quantile,
                  filters: q.filters,
                  filtersNe: q.filtersNe,
                  groupBy: q.groupBy,
                });
                return {
                  label: q.label,
                  matrix: res?.data ?? EMPTY_MATRIX,
                };
              } catch {
                return { label: q.label, matrix: EMPTY_MATRIX };
              }
            }),
          );
          return [id, mergeNamedSeries(parts)] as const;
        });

        // 分别运行，各自有独立并发上限
        const [instantResults, nodeCountResults, multiRangeResults] =
          await Promise.all([
            runWithConcurrencyLimit(instantTasks, concurrency),
            runWithConcurrencyLimit(nodeCountTasks, concurrency),
            runWithConcurrencyLimit(multiRangeTasks, concurrency),
          ]);

        if (cancelled) return;

        setData({
          instant: Object.fromEntries([...instantResults, ...nodeCountResults]),
          series: Object.fromEntries(multiRangeResults),
          loading: false,
        });
      } catch (err) {
        if (cancelled) return;
        setData((prev) => ({
          ...prev,
          loading: false,
          error: err instanceof Error ? err.message : 'Unknown error',
        }));
      }
    }

    fetchAll();
    return () => {
      cancelled = true;
    };
  }, [panelIds, instance, job, timeRange, clusterId, refreshKey, concurrency]);

  return data;
}
