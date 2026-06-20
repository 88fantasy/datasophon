import { useEffect, useRef, useState } from 'react';
import type { PrometheusMatrix, PrometheusVector } from './charts/promql';
import {
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from './charts/promql';
import type { PanelDef, RangePanelDef } from './panelTypes';
import { TIME_RANGE_SECONDS } from './panelTypes';
import { queryInstant, queryRange } from './service';
import type { TimeSeriesPoint } from './types';

// ─── 并发限流器 ────────────────────────────────────────────────────────────────
// 权威实现（原 DorisMonitor/hooks/useDorisMonitorDashboard.ts），统一供全部看板使用。

/** 在并发上限内运行任务组，供测试和外部调用。 */
export async function runWithConcurrencyLimit<T>(
  tasks: Array<() => Promise<T>>,
  limit: number,
): Promise<T[]> {
  const schedule = createConcurrencyLimiter(limit);
  return Promise.all(tasks.map((task) => schedule(task)));
}

function createConcurrencyLimiter(limit: number) {
  const maxConcurrency = Math.max(1, limit);
  let active = 0;
  const queue: Array<{
    task: () => Promise<unknown>;
    resolve: (value: unknown) => void;
    reject: (reason?: unknown) => void;
  }> = [];

  function runNext() {
    if (active >= maxConcurrency) return;
    const item = queue.shift();
    if (!item) return;

    active += 1;
    item
      .task()
      .then(item.resolve, item.reject)
      .finally(() => {
        active -= 1;
        runNext();
      });
  }

  return function schedule<T>(task: () => Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      queue.push({
        task,
        resolve: resolve as (value: unknown) => void,
        reject,
      });
      runNext();
    });
  };
}

// ─── 接口 ──────────────────────────────────────────────────────────────────────

/**
 * 额外裸查询（不对应 panelQueries 中的面板），如用于派生选择器的 `up` 查询。
 * kind='instant' 返回 PrometheusVector，kind='range' 返回 PrometheusMatrix。
 */
export interface ExtraQuery {
  query: string;
  kind: 'instant' | 'range';
}

export interface UseDashboardDataParams {
  /** 面板查询配置（来自各看板 panelQueries.ts） */
  panelQueries: Record<string, PanelDef>;
  /** 变量替换函数（将 panelQueries 中的 $占位符 替换为变量值） */
  replaceVars: (promql: string, vars: Record<string, string>) => string;
  /** 当前变量（instance/job/cluster 等） */
  variables: Record<string, string>;
  /**
   * 本轮实际要拉取的面板 ID 列表。
   *
   * ⚠️ 多 segment 看板（如 Doris）必须按当前激活 segment 过滤后传入，
   * 不得传全集——避免一次性拉全部面板导致后端超时。
   * 单 segment 看板可传全量面板 ID 数组。
   */
  panelIds: string[];
  /**
   * 额外裸查询（键名自定义，用于选择器派生、表格数据等）。
   * 结果通过返回值的 extras[key] 取回，页面侧自行转换。
   *
   * ⚠️ 必须使用模块级常量（在函数外声明的 `const EXTRAS = ...`），
   * 不得传入内联对象字面量——extras 在 useEffect 依赖数组中，
   * 内联对象每次渲染产生新引用，会导致无限重拉。
   */
  extras?: Record<string, ExtraQuery>;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
  /** 并发请求数上限，默认 4（统一修复原各看板无界 Promise.all） */
  concurrency?: number;
}

export interface DashboardData {
  /** instant 面板结果（vectorToScalar 已转换为 number），按 panelId 索引 */
  instant: Record<string, number>;
  /** range/multi-range 面板结果，按 panelId 索引 */
  series: Record<string, TimeSeriesPoint[]>;
  /** 额外裸查询原始结果（页面侧用 deriveInstancesAndJobs/vectorToTableRows 等转换） */
  extras: Record<string, PrometheusVector | PrometheusMatrix>;
  loading: boolean;
  error?: string;
}

// ─── Hook ──────────────────────────────────────────────────────────────────────

/**
 * 通用监控看板数据拉取 hook。
 *
 * 编排机制（instant/range/multi-range 执行、并发限流、时间窗口、cancelled 清理）
 * 统一在此处理，各看板只需提供 panelQueries + replaceVars + panelIds。
 *
 * 多 segment 看板须在外部按 activeSegment 过滤 panelIds，切 Tab 触发重拉。
 */
export function useDashboardData({
  panelQueries,
  replaceVars,
  variables,
  panelIds,
  extras,
  timeRange,
  clusterId = 1,
  refreshKey,
  concurrency = 4,
}: UseDashboardDataParams): DashboardData {
  const [data, setData] = useState<DashboardData>({
    instant: {},
    series: Object.fromEntries(panelIds.map((id) => [id, []])),
    extras: {},
    loading: true,
  });

  // replaceVars/panelQueries 是纯函数/模块常量，语义不变但每次渲染可能是新引用。
  // 用 ref 持有最新值，从 useEffect 依赖数组移除，避免 setData→re-render→新引用→无限循环。
  const replaceVarsRef = useRef(replaceVars);
  replaceVarsRef.current = replaceVars;
  const panelQueriesRef = useRef(panelQueries);
  panelQueriesRef.current = panelQueries;

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      setData((prev) => ({ ...prev, loading: true }));

      try {
        const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
        const end = Math.floor(Date.now() / 1000);
        const start = end - rangeSeconds;
        const step = Math.max(15, Math.floor(rangeSeconds / 200));
        const schedule = createConcurrencyLimiter(concurrency);

        // ── 按面板类型执行查询 ───────────────────────────────────────────────

        const _replaceVars = replaceVarsRef.current;
        const _panelQueries = panelQueriesRef.current;

        async function fetchInstant(panelId: string): Promise<number> {
          const def = _panelQueries[panelId];
          if (def.type !== 'instant') return 0;
          try {
            const res = await schedule(() =>
              queryInstant({
                query: _replaceVars(def.promql, variables),
                time: end,
                clusterId,
              }),
            );
            return res?.data ? vectorToScalar(res.data) : 0;
          } catch {
            return 0;
          }
        }

        async function fetchRange(panelId: string): Promise<TimeSeriesPoint[]> {
          const def = _panelQueries[panelId];
          if (def.type !== 'range') return [];
          const rangeDef = def as RangePanelDef;
          try {
            const res = await schedule(() =>
              queryRange({
                query: _replaceVars(rangeDef.promql, variables),
                start,
                end,
                step,
                clusterId,
              }),
            );
            return res?.data
              ? matrixToSeries(res.data, rangeDef.seriesKey)
              : [];
          } catch {
            return [];
          }
        }

        async function fetchMultiRange(
          panelId: string,
        ): Promise<TimeSeriesPoint[]> {
          const def = _panelQueries[panelId];
          if (def.type !== 'multi-range') return [];
          const parts = await Promise.all(
            def.queries.map(async ({ label, promql }) => {
              try {
                const res = await schedule(() =>
                  queryRange({
                    query: _replaceVars(promql, variables),
                    start,
                    end,
                    step,
                    clusterId,
                  }),
                );
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
          return mergeNamedSeries(parts);
        }

        // ── 额外裸查询 ─────────────────────────────────────────────────────

        async function fetchExtra(
          extra: ExtraQuery,
        ): Promise<PrometheusVector | PrometheusMatrix> {
          if (extra.kind === 'instant') {
            try {
              const res = await schedule(() =>
                queryInstant({ query: extra.query, time: end, clusterId }),
              );
              return res?.data ?? { resultType: 'vector' as const, result: [] };
            } catch {
              return { resultType: 'vector' as const, result: [] };
            }
          } else {
            try {
              const res = await schedule(() =>
                queryRange({ query: extra.query, start, end, step, clusterId }),
              );
              return res?.data ?? { resultType: 'matrix' as const, result: [] };
            } catch {
              return { resultType: 'matrix' as const, result: [] };
            }
          }
        }

        // ── 并行拉取 ───────────────────────────────────────────────────────

        const instantIds = panelIds.filter(
          (id) => _panelQueries[id]?.type === 'instant',
        );
        const rangeIds = panelIds.filter(
          (id) => _panelQueries[id]?.type === 'range',
        );
        const multiRangeIds = panelIds.filter(
          (id) => _panelQueries[id]?.type === 'multi-range',
        );
        const extraEntries = Object.entries(extras ?? {});

        const [instantValues, rangeValues, multiRangeValues, extraValues] =
          await Promise.all([
            Promise.all(
              instantIds.map((id) =>
                fetchInstant(id).then((v) => [id, v] as const),
              ),
            ),
            Promise.all(
              rangeIds.map((id) =>
                fetchRange(id).then((v) => [id, v] as const),
              ),
            ),
            Promise.all(
              multiRangeIds.map((id) =>
                fetchMultiRange(id).then((v) => [id, v] as const),
              ),
            ),
            Promise.all(
              extraEntries.map(([key, extra]) =>
                fetchExtra(extra).then((v) => [key, v] as const),
              ),
            ),
          ]);

        if (cancelled) return;

        setData({
          instant: Object.fromEntries(instantValues),
          series: Object.fromEntries([...rangeValues, ...multiRangeValues]),
          extras: Object.fromEntries(extraValues),
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
  }, [
    variables,
    panelIds,
    extras,
    timeRange,
    clusterId,
    refreshKey,
    concurrency,
    // replaceVars/panelQueries 通过 ref 读取，不放入依赖数组，避免 inline 函数导致无限循环
  ]);

  return data;
}
