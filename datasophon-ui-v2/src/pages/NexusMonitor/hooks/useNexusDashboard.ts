import { useEffect, useState } from 'react';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import {
  deriveInstancesAndJobs,
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  type NexusDashboardVariables,
  PANEL_QUERIES,
  replaceNexusVars,
  TIME_RANGE_SECONDS,
} from '../panelQueries';

export interface NexusInstantValues {
  uptime: number;
  heapRatio: number;
  fdRatio: number;
  readonlyEnabled: number;
  jvmThreads: number;
  deadlockThreads: number;
}

export interface NexusDashboardData {
  instant: NexusInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  jobs: string[];
  loading: boolean;
  error?: string;
}

const DEFAULT_INSTANT: NexusInstantValues = {
  uptime: 0,
  heapRatio: 0,
  fdRatio: 0,
  readonlyEnabled: 0,
  jvmThreads: 0,
  deadlockThreads: 0,
};

const RANGE_PANEL_IDS = Array.from(
  { length: 12 },
  (_, index) => `N${String(index + 7).padStart(2, '0')}`,
);

const EMPTY_SERIES = Object.fromEntries(
  RANGE_PANEL_IDS.map((panelId) => [panelId, []]),
);

export interface UseNexusDashboardParams {
  variables: NexusDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useNexusDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseNexusDashboardParams): NexusDashboardData {
  const [data, setData] = useState<NexusDashboardData>({
    instant: DEFAULT_INSTANT,
    series: EMPTY_SERIES,
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
        const step = Math.max(15, Math.floor(rangeSeconds / 200));

        async function instant(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'instant') return null;
          const res = await queryInstant({
            query: replaceNexusVars(def.promql, variables),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        async function panelSeries(panelId: string) {
          const def = PANEL_QUERIES[panelId];

          if (def.type === 'range') {
            const res = await queryRange({
              query: replaceNexusVars(def.promql, variables),
              start,
              end,
              step,
              clusterId,
            });
            return res?.data ? matrixToSeries(res.data, def.seriesKey) : [];
          }

          if (def.type === 'multi-range') {
            const parts = await Promise.all(
              def.queries.map(async ({ label, promql }) => {
                const res = await queryRange({
                  query: replaceNexusVars(promql, variables),
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

          return [];
        }

        const [n01, n02, n03, n04, n05, n06, labelVector, ...rangeValues] =
          await Promise.all([
            instant('N01'),
            instant('N02'),
            instant('N03'),
            instant('N04'),
            instant('N05'),
            instant('N06'),
            queryInstant({
              query: 'jvm_vm_uptime{instance=~".+",job=~".+"}',
              time: end,
              clusterId,
            }).then(
              (res) =>
                res?.data ?? { resultType: 'vector' as const, result: [] },
            ),
            ...RANGE_PANEL_IDS.map((panelId) => panelSeries(panelId)),
          ]);

        if (cancelled) return;

        const { instances, jobs } = deriveInstancesAndJobs(labelVector);

        setData({
          instant: {
            uptime: n01 ? vectorToScalar(n01) : 0,
            heapRatio: n02 ? vectorToScalar(n02) : 0,
            fdRatio: n03 ? vectorToScalar(n03) : 0,
            readonlyEnabled: n04 ? vectorToScalar(n04) : 0,
            jvmThreads: n05 ? vectorToScalar(n05) : 0,
            deadlockThreads: n06 ? vectorToScalar(n06) : 0,
          },
          series: Object.fromEntries(
            RANGE_PANEL_IDS.map((panelId, index) => [
              panelId,
              rangeValues[index],
            ]),
          ),
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
  }, [variables, timeRange, clusterId, refreshKey]);

  return data;
}
