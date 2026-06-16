import { useEffect, useState } from 'react';
import type { RangePanelDef } from '../../PrometheusMonitor/panelQueries';
import { TIME_RANGE_SECONDS } from '../../PrometheusMonitor/panelQueries';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import {
  deriveInstancesAndJobs,
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  DORIS_RANGE_PANEL_IDS,
  getDorisSegmentPanelIds,
  PANEL_QUERIES,
  replaceDorisVars,
  type DorisDashboardSegment,
  type DorisDashboardVariables,
} from '../panelQueries';

export interface DorisInstantValues {
  feNodeCount: number;
  feAliveCount: number;
  beNodeCount: number;
  beAliveCount: number;
  usedCapacityBytes: number;
  totalCapacityBytes: number;
}

export interface DorisDashboardData {
  instant: DorisInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  clusters: string[];
  feInstances: string[];
  beInstances: string[];
  loading: boolean;
  error?: string;
}

interface UseDorisMonitorDashboardParams {
  variables: DorisDashboardVariables;
  activeSegment: DorisDashboardSegment;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

const DORIS_PROMETHEUS_REQUEST_CONCURRENCY = 4;

const DEFAULT_INSTANT: DorisInstantValues = {
  feNodeCount: 0,
  feAliveCount: 0,
  beNodeCount: 0,
  beAliveCount: 0,
  usedCapacityBytes: 0,
  totalCapacityBytes: 0,
};

const EMPTY_SERIES: Record<string, TimeSeriesPoint[]> = Object.fromEntries(
  DORIS_RANGE_PANEL_IDS.map((id) => [id, []]),
);

const INSTANT_KEY_MAP: Record<keyof DorisInstantValues, string> = {
  feNodeCount: 'DO-A01',
  feAliveCount: 'DO-A02',
  beNodeCount: 'DO-A03',
  beAliveCount: 'DO-A04',
  usedCapacityBytes: 'DO-A05',
  totalCapacityBytes: 'DO-A06',
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

export function useDorisMonitorDashboard({
  variables,
  activeSegment,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDorisMonitorDashboardParams): DorisDashboardData {
  const [data, setData] = useState<DorisDashboardData>({
    instant: DEFAULT_INSTANT,
    series: EMPTY_SERIES,
    clusters: [],
    feInstances: [],
    beInstances: [],
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
          DORIS_PROMETHEUS_REQUEST_CONCURRENCY,
        );
        const activePanelIds = getDorisSegmentPanelIds(activeSegment);
        const activeInstantEntries = Object.entries(INSTANT_KEY_MAP).filter(
          ([, panelId]) => activePanelIds.includes(panelId),
        );
        const activeRangePanelIds = activePanelIds.filter(
          (panelId) => PANEL_QUERIES[panelId].type !== 'instant',
        );

        async function instant(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'instant') return null;
          try {
            const res = await schedule(() =>
              queryInstant({
                query: replaceDorisVars(def.promql, variables),
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
                query: replaceDorisVars(rangeDef.promql, variables),
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
                    query: replaceDorisVars(promql, variables),
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

        const [instantEntries, seriesEntries, clustersVector, feUp, beUp] =
          await Promise.all([
            Promise.all(
              activeInstantEntries.map(async ([key, panelId]) => {
                const value = await instant(panelId);
                return [key, value ? vectorToScalar(value) : 0] as const;
              }),
            ),
            Promise.all(
              activeRangePanelIds.map(async (panelId) => {
                const def = PANEL_QUERIES[panelId];
                const value =
                  def.type === 'multi-range'
                    ? await multiRange(panelId)
                    : await range(panelId);
                return [panelId, value] as const;
              }),
            ),
            schedule(() =>
              queryInstant({ query: 'up{group="fe"}', time: end, clusterId }),
            )
              .then(
                (res) =>
                  res?.data ?? { resultType: 'vector' as const, result: [] },
              )
              .catch(() => ({ resultType: 'vector' as const, result: [] })),
            schedule(() =>
              queryInstant({
                query: `up{group="fe", job="${variables.cluster || 'doris'}"}`,
                time: end,
                clusterId,
              }),
            )
              .then(
                (res) =>
                  res?.data ?? { resultType: 'vector' as const, result: [] },
              )
              .catch(() => ({ resultType: 'vector' as const, result: [] })),
            schedule(() =>
              queryInstant({
                query: `up{group="be", job="${variables.cluster || 'doris'}"}`,
                time: end,
                clusterId,
              }),
            )
              .then(
                (res) =>
                  res?.data ?? { resultType: 'vector' as const, result: [] },
              )
              .catch(() => ({ resultType: 'vector' as const, result: [] })),
          ]);

        if (cancelled) return;

        setData({
          instant: {
            ...DEFAULT_INSTANT,
            ...Object.fromEntries(instantEntries),
          },
          series: {
            ...EMPTY_SERIES,
            ...Object.fromEntries(seriesEntries),
          },
          clusters: deriveInstancesAndJobs(clustersVector).jobs,
          feInstances: deriveInstancesAndJobs(feUp).instances,
          beInstances: deriveInstancesAndJobs(beUp).instances,
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
  }, [variables, activeSegment, timeRange, clusterId, refreshKey]);

  return data;
}
