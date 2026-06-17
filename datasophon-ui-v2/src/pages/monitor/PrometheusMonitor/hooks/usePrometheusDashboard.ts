import { useEffect, useState } from 'react';
import type { DashboardVariables } from '../../_shared/charts/promql';
import {
  deriveInstancesAndJobs,
  matrixToSeries,
  mergeNamedSeries,
  replaceVars,
  vectorToScalar,
  vectorToTableRows,
} from '../../_shared/charts/promql';
import type { InstantPanelDef } from '../../_shared/panelTypes';
import { queryInstant, queryRange } from '../../_shared/service';
import type { PrometheusTableRow, TimeSeriesPoint } from '../../_shared/types';
import { PANEL_QUERIES, TIME_RANGE_SECONDS } from '../panelQueries';

// ─── 返回类型 ─────────────────────────────────────────────────────────────────

export interface InstantValues {
  uptime: number;
  totalSeries: number;
  memoryChunks: number;
  reloadFailures: number;
  missedIterations: number;
  skippedScrapes: number;
}

export interface DashboardData {
  instant: InstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  down: PrometheusTableRow[];
  instances: string[];
  jobs: string[];
  loading: boolean;
  error?: string;
}

// ─── 默认值（加载中/错误时占位）────────────────────────────────────────────────

const DEFAULT_INSTANT: InstantValues = {
  uptime: 0,
  totalSeries: 0,
  memoryChunks: 0,
  reloadFailures: 0,
  missedIterations: 0,
  skippedScrapes: 0,
};

const EMPTY_SERIES: Record<string, TimeSeriesPoint[]> = Object.fromEntries(
  [
    'P08',
    'P09',
    'P10',
    'P11',
    'P12',
    'P13',
    'P14',
    'P15',
    'P16',
    'P17',
    'P18',
    'P19',
    'P20',
    'P21',
    'P22',
    'P23',
    'P24',
  ].map((k) => [k, []]),
);

// ─── Hook ─────────────────────────────────────────────────────────────────────

export interface UsePrometheusDashboardParams {
  variables: DashboardVariables;
  /** 时间范围快捷键，如 '1h'、'6h' */
  timeRange: string;
  /** 集群 ID（后端当前忽略） */
  clusterId?: number;
  /** 每次 refreshKey 变化触发重新拉取，由 toolbar 倒计时 onRefresh 驱动 */
  refreshKey: number;
}

/**
 * 聚合 hook：一次拉取全部 24 个面板数据。
 *
 * 设计原则：
 * - 不在 hook 内起 setInterval（toolbar 已实现倒计时 + onRefresh）
 * - Promise.all 并行请求，P22 等有 > 0 过滤的 PromQL 可能返回空 vector/matrix，
 *   需容错处理（空数组）
 * - 单一 loading 状态，整批完成后一次性更新，避免面板逐个闪烁
 */
export function usePrometheusDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UsePrometheusDashboardParams): DashboardData {
  const [data, setData] = useState<DashboardData>({
    instant: DEFAULT_INSTANT,
    series: EMPTY_SERIES,
    down: [],
    instances: [],
    jobs: [],
    loading: true,
  });

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      setData((prev) => ({ ...prev, loading: true }));

      try {
        const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
        const end = Math.floor(Date.now() / 1000);
        const start = end - rangeSeconds;
        // 步长：最小 15s；让返回点数约 200 个
        const step = Math.max(15, Math.floor(rangeSeconds / 200));

        const vars = variables;

        // ── 辅助：执行 instant query ────────────────────────────────────────
        async function instant(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'instant') return null;
          const res = await queryInstant({
            query: replaceVars(def.promql, vars),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        // ── 辅助：执行 range query ──────────────────────────────────────────
        async function range(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'range') return null;
          const res = await queryRange({
            query: replaceVars(def.promql, vars),
            start,
            end,
            step,
            clusterId,
          });
          return res?.data ?? null;
        }

        // ── 辅助：执行 multi-range query ────────────────────────────────────
        async function multiRange(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'multi-range') return [];
          const parts = await Promise.all(
            def.queries.map(async ({ label, promql }) => {
              const res = await queryRange({
                query: replaceVars(promql, vars),
                start,
                end,
                step,
                clusterId,
              });
              return {
                label,
                matrix: res?.data ?? {
                  resultType: 'matrix' as const,
                  result: [],
                },
              };
            }),
          );
          return mergeNamedSeries(parts);
        }

        // ── 并行拉取全部面板 ─────────────────────────────────────────────────
        const [
          // R1 instant
          v01,
          v02,
          v03,
          v04,
          v05,
          v06,
          // R2
          v07Down,
          v07Up,
          // range panels
          p08,
          p09,
          p10,
          p11,
          // multi-range panels
          p12,
          p13,
          p15,
          p16,
          p18,
          p14,
          p17,
          p19,
          p20,
          p21,
          p22,
          p23,
          p24,
        ] = await Promise.all([
          // instant stats
          instant('P01'),
          instant('P02'),
          instant('P03'),
          instant('P04'),
          instant('P05'),
          instant('P06'),

          // P07：Currently Down（instant vector → TableRow[]）
          queryInstant({
            query: replaceVars(
              (PANEL_QUERIES.P07 as InstantPanelDef).promql,
              vars,
            ),
            time: end,
            clusterId,
          }).then(
            (r) => r?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          // up 查询（派生 instances/jobs）
          queryInstant({ query: 'up', time: end, clusterId }).then(
            (r) => r?.data ?? { resultType: 'vector' as const, result: [] },
          ),

          // R2–R8 range panels
          range('P08').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P09').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P10').then((m) => (m ? matrixToSeries(m, 'scrape_job') : [])),
          range('P11').then((m) => (m ? matrixToSeries(m, 'scrape_job') : [])),
          multiRange('P12'),
          range('P13').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P15').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P16').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P18').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          multiRange('P14'),
          multiRange('P17'),
          multiRange('P19'),
          range('P20').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P21').then((m) => (m ? matrixToSeries(m, 'slice') : [])),
          multiRange('P22'),
          range('P23').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
          range('P24').then((m) => (m ? matrixToSeries(m, 'instance') : [])),
        ]);

        if (cancelled) return;

        const { instances, jobs } = deriveInstancesAndJobs(v07Up);

        setData({
          instant: {
            uptime: v01 ? vectorToScalar(v01) : 0,
            totalSeries: v02 ? vectorToScalar(v02) : 0,
            memoryChunks: v03 ? vectorToScalar(v03) : 0,
            reloadFailures: v04 ? vectorToScalar(v04) : 0,
            missedIterations: v05 ? vectorToScalar(v05) : 0,
            skippedScrapes: v06 ? vectorToScalar(v06) : 0,
          },
          series: {
            P08: p08,
            P09: p09,
            P10: p10,
            P11: p11,
            P12: p12,
            P13: p13,
            P14: p14,
            P15: p15,
            P16: p16,
            P17: p17,
            P18: p18,
            P19: p19,
            P20: p20,
            P21: p21,
            P22: p22,
            P23: p23,
            P24: p24,
          },
          down: vectorToTableRows(v07Down),
          instances,
          jobs,
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
    // refreshKey 变化 → 重新拉取（由 toolbar 倒计时驱动）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variables, timeRange, clusterId, refreshKey]);

  return data;
}
