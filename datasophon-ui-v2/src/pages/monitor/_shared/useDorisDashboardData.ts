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
import {
  mergeNamedSeries,
  vectorToScalar,
} from './charts/promql';
import type { DorisPanelDescriptor } from './dorisService';
import { queryDorisInstant, queryDorisRange } from './dorisService';
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

/**
 * 从 Doris OTel 表取数的监控看板 hook。
 *
 * 与 useDashboardData 并行——不改动已有 Prometheus 取数路径，
 * 只供改写为 Doris 描述符的看板（如 NexusMonitor）调用。
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
        const multiRangeIds = panelIds.filter(
          (id) => _descriptors[id]?.type === 'multi-range',
        );

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
              });
              return [id, res?.data ? vectorToScalar(res.data) : 0] as const;
            } catch {
              return [id, 0] as const;
            }
          });

        const multiRangeTasks: Array<
          () => Promise<readonly [string, TimeSeriesPoint[]]>
        > = multiRangeIds.map((id) => async () => {
          const def = _descriptors[id];
          if (def.type !== 'multi-range') return [id, []] as const;
          const parts = await Promise.all(
            def.queries.map(async ({ label, metric, rate, scale, table, quantile }) => {
              try {
                const res = await queryDorisRange({
                  metric,
                  rateWindow: rate,
                  scale,
                  instance,
                  job,
                  start,
                  end,
                  step,
                  clusterId,
                  table,
                  quantile,
                });
                return {
                  label,
                  matrix: res?.data ?? {
                    resultType: 'matrix' as const,
                    result: [],
                  },
                };
              } catch {
                return {
                  label,
                  matrix: { resultType: 'matrix' as const, result: [] },
                };
              }
            }),
          );
          return [id, mergeNamedSeries(parts)] as const;
        });

        // 分别运行，各自有独立并发上限（合并为 union type 会破坏类型安全）
        const [instantResults, multiRangeResults] = await Promise.all([
          runWithConcurrencyLimit(instantTasks, concurrency),
          runWithConcurrencyLimit(multiRangeTasks, concurrency),
        ]);

        if (cancelled) return;

        setData({
          instant: Object.fromEntries(instantResults),
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
