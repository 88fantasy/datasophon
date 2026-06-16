import { useEffect, useState } from 'react';
import type { RangePanelDef } from '../../PrometheusMonitor/panelQueries';
import { TIME_RANGE_SECONDS } from '../../PrometheusMonitor/panelQueries';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import {
  deriveInstancesAndJobs,
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  PANEL_QUERIES,
  replaceDSVars,
  type DSDashboardVariables,
} from '../panelQueries';

export interface DSInstantValues {
  taskTotal: number;
  taskSuccessRate: number;
  quartzJobTotal: number;
  quartzJobSuccessRate: number;
  uptime: number;
  heapUsedPercent: number;
  nonHeapUsedPercent: number;
}

export interface DSDashboardData {
  instant: DSInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

interface UseDSMonitorDashboardParams {
  variables: DSDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

const DS_PROMETHEUS_REQUEST_CONCURRENCY = 4;

const DEFAULT_INSTANT: DSInstantValues = {
  taskTotal: 0,
  taskSuccessRate: 0,
  quartzJobTotal: 0,
  quartzJobSuccessRate: 0,
  uptime: 0,
  heapUsedPercent: 0,
  nonHeapUsedPercent: 0,
};

const SERIES_IDS = [
  'D-A01',
  'D-A02',
  'D-A03',
  'D-A04',
  'D-A05',
  'D-A06',
  'D-B05',
  'D-B06',
  'D-B07',
  'D-B08',
  'D-B09',
  'D-B10',
  'D-B11',
  'D-B12',
  'D-B13',
  'D-C04',
  'D-C05',
  'D-C06',
  'D-C07',
  'D-C08',
  'D-C09',
  'D-C10',
  'D-C11',
  'D-C12',
  'D-C13',
];

const EMPTY_SERIES: Record<string, TimeSeriesPoint[]> = Object.fromEntries(
  SERIES_IDS.map((id) => [id, []]),
);

const INSTANT_KEY_MAP: Record<keyof DSInstantValues, string> = {
  taskTotal: 'D-B01',
  taskSuccessRate: 'D-B02',
  quartzJobTotal: 'D-B03',
  quartzJobSuccessRate: 'D-B04',
  uptime: 'D-C01',
  heapUsedPercent: 'D-C02',
  nonHeapUsedPercent: 'D-C03',
};

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

export function useDSMonitorDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDSMonitorDashboardParams): DSDashboardData {
  const [data, setData] = useState<DSDashboardData>({
    instant: DEFAULT_INSTANT,
    series: EMPTY_SERIES,
    instances: [],
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
        const step = Math.max(15, Math.floor(rangeSeconds / 200));
        const schedule = createConcurrencyLimiter(
          DS_PROMETHEUS_REQUEST_CONCURRENCY,
        );

        async function instant(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'instant') return null;
          try {
            const res = await schedule(() =>
              queryInstant({
                query: replaceDSVars(def.promql, variables),
                time: end,
                clusterId,
              }),
            );
            return res?.data ?? null;
          } catch {
            return null;
          }
        }

        async function range(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'range') return [];
          const rangeDef = def as RangePanelDef;
          try {
            const res = await schedule(() =>
              queryRange({
                query: replaceDSVars(rangeDef.promql, variables),
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

        async function multiRange(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'multi-range') return [];
          const parts = await Promise.all(
            def.queries.map(async ({ label, promql }) => {
              try {
                const res = await schedule(() =>
                  queryRange({
                    query: replaceDSVars(promql, variables),
                    start,
                    end,
                    step,
                    clusterId,
                  }),
                );
                return {
                  label,
                  matrix:
                    res?.data ?? { resultType: 'matrix' as const, result: [] },
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

        const [instantEntries, seriesEntries, upVector] = await Promise.all([
          Promise.all(
            Object.entries(INSTANT_KEY_MAP).map(async ([key, panelId]) => {
              const value = await instant(panelId);
              return [key, value ? vectorToScalar(value) : 0] as const;
            }),
          ),
          Promise.all(
            SERIES_IDS.map(async (panelId) => {
              const def = PANEL_QUERIES[panelId];
              const value =
                def.type === 'multi-range'
                  ? await multiRange(panelId)
                  : await range(panelId);
              return [panelId, value] as const;
            }),
          ),
          schedule(() =>
            queryInstant({
              query: `up{application="${variables.application || 'master-server'}"}`,
              time: end,
              clusterId,
            }),
          )
            .then(
              (res) => res?.data ?? { resultType: 'vector' as const, result: [] },
            )
            .catch(() => ({ resultType: 'vector' as const, result: [] })),
        ]);

        if (cancelled) return;

        const { instances } = deriveInstancesAndJobs(upVector);

        setData({
          instant: {
            ...DEFAULT_INSTANT,
            ...Object.fromEntries(instantEntries),
          },
          series: {
            ...EMPTY_SERIES,
            ...Object.fromEntries(seriesEntries),
          },
          instances,
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
  }, [variables, timeRange, clusterId, refreshKey]);

  return data;
}
