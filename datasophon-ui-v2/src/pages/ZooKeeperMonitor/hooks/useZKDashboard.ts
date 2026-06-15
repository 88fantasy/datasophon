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
  PANEL_QUERIES,
  replaceZKVars,
  TIME_RANGE_SECONDS,
  type ZKDashboardVariables,
} from '../panelQueries';

export interface ZKInstantValues {
  quorumSize: number;
  leaderUptime: number;
  jvmThreads: number;
  deadlockedThreads: number;
  aliveConnections: number;
  openFileDescriptors: number;
}

export interface ZKDashboardData {
  instant: ZKInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  jobs: string[];
  loading: boolean;
  error?: string;
}

const DEFAULT_INSTANT: ZKInstantValues = {
  quorumSize: 0,
  leaderUptime: 0,
  jvmThreads: 0,
  deadlockedThreads: 0,
  aliveConnections: 0,
  openFileDescriptors: 0,
};

const RANGE_PANEL_IDS = Array.from({ length: 17 }, (_, index) =>
  `Z${String(index + 7).padStart(2, '0')}`,
);

const EMPTY_SERIES = Object.fromEntries(
  RANGE_PANEL_IDS.map((panelId) => [panelId, []]),
);

export interface UseZKDashboardParams {
  variables: ZKDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useZKDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseZKDashboardParams): ZKDashboardData {
  const [data, setData] = useState<ZKDashboardData>({
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
            query: replaceZKVars(def.promql, variables),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        async function panelSeries(panelId: string) {
          const def = PANEL_QUERIES[panelId];

          if (def.type === 'range') {
            const res = await queryRange({
              query: replaceZKVars(def.promql, variables),
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
                  query: replaceZKVars(promql, variables),
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

        const [
          z01,
          z02,
          z03,
          z04,
          z05,
          z06,
          upVector,
          ...rangeValues
        ] = await Promise.all([
          instant('Z01'),
          instant('Z02'),
          instant('Z03'),
          instant('Z04'),
          instant('Z05'),
          instant('Z06'),
          queryInstant({ query: 'up', time: end, clusterId }).then(
            (res) =>
              res?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          ...RANGE_PANEL_IDS.map((panelId) => panelSeries(panelId)),
        ]);

        if (cancelled) return;

        const { instances, jobs } = deriveInstancesAndJobs(upVector);

        setData({
          instant: {
            quorumSize: z01 ? vectorToScalar(z01) : 0,
            leaderUptime: z02 ? vectorToScalar(z02) : 0,
            jvmThreads: z03 ? vectorToScalar(z03) : 0,
            deadlockedThreads: z04 ? vectorToScalar(z04) : 0,
            aliveConnections: z05 ? vectorToScalar(z05) : 0,
            openFileDescriptors: z06 ? vectorToScalar(z06) : 0,
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
