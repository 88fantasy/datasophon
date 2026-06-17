import { useEffect, useMemo, useState } from 'react';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import {
  deriveInstancesAndJobs,
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  MOCK_INSTANCES,
  MOCK_JOBS,
  instantValues as mockInstantValues,
} from '../mock/mysqlMockData';
import {
  type MySQLDashboardVariables,
  PANEL_QUERIES,
  replaceMySQLVars,
  TIME_RANGE_SECONDS,
} from '../panelQueries';

export interface MySQLInstantValues {
  uptime: number;
  currentQps: number;
  connectionsUsedPercent: number;
  innodbBufferPool: number;
  slowQueriesPerSecond: number;
  abortedConnectionsPerSecond: number;
}

export interface MySQLDashboardData {
  instant: MySQLInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  jobs: string[];
  loading: boolean;
  rateInterval: string;
  error?: string;
}

const DEFAULT_INSTANT: MySQLInstantValues = {
  uptime: mockInstantValues.uptime,
  currentQps: mockInstantValues.currentQps,
  connectionsUsedPercent: mockInstantValues.connectionsUsedPercent,
  innodbBufferPool: mockInstantValues.innodbBufferPool,
  slowQueriesPerSecond: mockInstantValues.slowQueriesPerSecond,
  abortedConnectionsPerSecond: mockInstantValues.abortedConnectionsPerSecond,
};

const RANGE_PANEL_IDS = Array.from(
  { length: 11 },
  (_, index) => `M${String(index + 7).padStart(2, '0')}`,
);

const EMPTY_SERIES = Object.fromEntries(
  RANGE_PANEL_IDS.map((panelId) => [panelId, []]),
);

export interface UseMySQLDashboardParams {
  variables: MySQLDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

function intervalToPromDuration(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600}h`;
  if (seconds % 60 === 0) return `${seconds / 60}m`;
  return `${seconds}s`;
}

function calcRateInterval(rangeSeconds: number): string {
  const scrapeIntervalSeconds = 15;
  const intervalSeconds = Math.max(
    scrapeIntervalSeconds * 4,
    Math.floor(rangeSeconds / 200),
  );

  return intervalToPromDuration(intervalSeconds);
}

export function useMySQLDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseMySQLDashboardParams): MySQLDashboardData {
  const rateInterval = useMemo(() => {
    const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
    return calcRateInterval(rangeSeconds);
  }, [timeRange]);

  const [data, setData] = useState<MySQLDashboardData>({
    instant: DEFAULT_INSTANT,
    series: EMPTY_SERIES,
    instances: MOCK_INSTANCES,
    jobs: MOCK_JOBS,
    loading: true,
    rateInterval,
  });

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      setData((prev) => ({ ...prev, loading: true, rateInterval }));

      try {
        const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
        const end = Math.floor(Date.now() / 1000);
        const start = end - rangeSeconds;
        const step = Math.max(15, Math.floor(rangeSeconds / 200));

        async function instant(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'instant') return null;
          const res = await queryInstant({
            query: replaceMySQLVars(def.promql, variables, rateInterval),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        async function panelSeries(panelId: string) {
          const def = PANEL_QUERIES[panelId];

          if (def.type === 'range') {
            const res = await queryRange({
              query: replaceMySQLVars(def.promql, variables, rateInterval),
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
                  query: replaceMySQLVars(promql, variables, rateInterval),
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

        const [m01, m02, m03, m04, m05, m06, mysqlUpVector, ...rangeValues] =
          await Promise.all([
            instant('M01'),
            instant('M02'),
            instant('M03'),
            instant('M04'),
            instant('M05'),
            instant('M06'),
            queryInstant({
              query: 'mysql_up',
              time: end,
              clusterId,
            }).then(
              (res) =>
                res?.data ?? { resultType: 'vector' as const, result: [] },
            ),
            ...RANGE_PANEL_IDS.map((panelId) => panelSeries(panelId)),
          ]);

        if (cancelled) return;

        const { instances, jobs } = deriveInstancesAndJobs(mysqlUpVector);

        setData({
          instant: {
            uptime: m01 ? vectorToScalar(m01) : 0,
            currentQps: m02 ? vectorToScalar(m02) : 0,
            connectionsUsedPercent: m03 ? vectorToScalar(m03) : 0,
            innodbBufferPool: m04 ? vectorToScalar(m04) : 0,
            slowQueriesPerSecond: m05 ? vectorToScalar(m05) : 0,
            abortedConnectionsPerSecond: m06 ? vectorToScalar(m06) : 0,
          },
          series: Object.fromEntries(
            RANGE_PANEL_IDS.map((panelId, index) => [
              panelId,
              rangeValues[index],
            ]),
          ),
          instances: instances.length > 0 ? instances : MOCK_INSTANCES,
          jobs: jobs.length > 0 ? jobs : MOCK_JOBS,
          loading: false,
          rateInterval,
        });
      } catch (err) {
        if (cancelled) return;
        setData((prev) => ({
          ...prev,
          loading: false,
          rateInterval,
          error: err instanceof Error ? err.message : 'Unknown error',
        }));
      }
    }

    fetchAll();
    return () => {
      cancelled = true;
    };
  }, [variables, timeRange, clusterId, refreshKey, rateInterval]);

  return data;
}
