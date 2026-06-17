import { useEffect, useMemo, useState } from 'react';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import {
  matrixToSeries,
  mergeNamedSeries,
  type PrometheusVector,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  kyuubiSeriesData,
  MOCK_CONN_TYPES,
  MOCK_INSTANCES,
  MOCK_OP_TYPES,
  instantValues as mockInstantValues,
} from '../mock/kyuubiMockData';
import {
  type KyuubiDashboardVariables,
  PANEL_QUERIES,
  replaceKyuubiVars,
  TIME_RANGE_SECONDS,
} from '../panelQueries';

export interface KyuubiInstantValues {
  instances: number;
  uptime: number;
  connectionOpened: number;
  engineTotal: number;
  execPoolThreads: number;
  operationErrorRate: number;
}

export interface KyuubiDashboardData {
  instant: KyuubiInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  connTypes: string[];
  opTypes: string[];
  trendInterval: string;
  loading: boolean;
  error?: string;
}

export interface UseKyuubiDashboardParams {
  variables: KyuubiDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export const KYUUBI_CONN_TYPES = [
  'connection_total_INTERACTIVE',
  'connection_total_BATCH',
];

export const KYUUBI_OP_TYPES = [
  'ExecuteStatement',
  'LaunchEngine',
  'GetSchemas',
  'GetTables',
  'GetColumns',
  'GetFunctions',
  'GetCatalogs',
  'GetTypeInfo',
];

const DEFAULT_INSTANT: KyuubiInstantValues = {
  instances: mockInstantValues.KY01,
  uptime: mockInstantValues.KY02,
  connectionOpened: mockInstantValues.KY03,
  engineTotal: mockInstantValues.KY04,
  execPoolThreads: mockInstantValues.KY05,
  operationErrorRate: mockInstantValues.KY06,
};

const RANGE_PANEL_IDS = Array.from(
  { length: 10 },
  (_, index) => `KY${String(index + 7).padStart(2, '0')}`,
);

const DEFAULT_SERIES = Object.fromEntries(
  RANGE_PANEL_IDS.map((panelId) => [panelId, kyuubiSeriesData[panelId] ?? []]),
);

function deriveKyuubiInstances(vector: PrometheusVector): string[] {
  return [
    ...new Set(
      vector.result
        .map((item) => item.metric.instance)
        .filter((instance): instance is string => Boolean(instance)),
    ),
  ];
}

function trendIntervalForRange(timeRange: string): string {
  if (timeRange === '5m' || timeRange === '15m') return '1m';
  if (timeRange === '6h' || timeRange === '24h') return '15m';
  if (timeRange === '7d') return '1h';
  return '5m';
}

export function useKyuubiDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseKyuubiDashboardParams): KyuubiDashboardData {
  const trendInterval = useMemo(
    () => trendIntervalForRange(timeRange),
    [timeRange],
  );
  const effectiveVariables = useMemo(
    () => ({ ...variables, trendInterval }),
    [variables, trendInterval],
  );

  const [data, setData] = useState<KyuubiDashboardData>({
    instant: DEFAULT_INSTANT,
    series: DEFAULT_SERIES,
    instances: MOCK_INSTANCES,
    connTypes: MOCK_CONN_TYPES,
    opTypes: MOCK_OP_TYPES,
    trendInterval,
    loading: true,
  });

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      setData((prev) => ({ ...prev, trendInterval, loading: true }));

      try {
        const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
        const end = Math.floor(Date.now() / 1000);
        const start = end - rangeSeconds;
        const step = Math.max(15, Math.floor(rangeSeconds / 200));

        async function instant(panelId: string) {
          const def = PANEL_QUERIES[panelId];
          if (def.type !== 'instant') return null;
          const res = await queryInstant({
            query: replaceKyuubiVars(def.promql, effectiveVariables),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        async function panelSeries(panelId: string) {
          const def = PANEL_QUERIES[panelId];

          if (def.type === 'range') {
            const res = await queryRange({
              query: replaceKyuubiVars(def.promql, effectiveVariables),
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
                  query: replaceKyuubiVars(promql, effectiveVariables),
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
          ky01,
          ky02,
          ky03,
          ky04,
          ky05,
          ky06,
          instanceVector,
          ...rangeValues
        ] = await Promise.all([
          instant('KY01'),
          instant('KY02'),
          instant('KY03'),
          instant('KY04'),
          instant('KY05'),
          instant('KY06'),
          queryInstant({
            query: replaceKyuubiVars(
              'kyuubi_jvm_uptime{$baseFilter}',
              effectiveVariables,
            ),
            time: end,
            clusterId,
          }).then(
            (res) => res?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          ...RANGE_PANEL_IDS.map((panelId) => panelSeries(panelId)),
        ]);

        if (cancelled) return;

        const discoveredInstances = deriveKyuubiInstances(instanceVector);

        setData({
          instant: {
            instances: ky01 ? vectorToScalar(ky01) : DEFAULT_INSTANT.instances,
            uptime: ky02 ? vectorToScalar(ky02) : DEFAULT_INSTANT.uptime,
            connectionOpened: ky03
              ? vectorToScalar(ky03)
              : DEFAULT_INSTANT.connectionOpened,
            engineTotal: ky04
              ? vectorToScalar(ky04)
              : DEFAULT_INSTANT.engineTotal,
            execPoolThreads: ky05
              ? vectorToScalar(ky05)
              : DEFAULT_INSTANT.execPoolThreads,
            operationErrorRate: ky06
              ? vectorToScalar(ky06)
              : DEFAULT_INSTANT.operationErrorRate,
          },
          series: Object.fromEntries(
            RANGE_PANEL_IDS.map((panelId, index) => [
              panelId,
              rangeValues[index].length > 0
                ? rangeValues[index]
                : (kyuubiSeriesData[panelId] ?? []),
            ]),
          ),
          instances:
            discoveredInstances.length > 0
              ? discoveredInstances
              : MOCK_INSTANCES,
          connTypes: MOCK_CONN_TYPES,
          opTypes: MOCK_OP_TYPES,
          trendInterval,
          loading: false,
        });
      } catch (err) {
        if (cancelled) return;
        setData((prev) => ({
          ...prev,
          trendInterval,
          loading: false,
          error: err instanceof Error ? err.message : 'Unknown error',
        }));
      }
    }

    fetchAll();
    return () => {
      cancelled = true;
    };
  }, [effectiveVariables, timeRange, clusterId, refreshKey, trendInterval]);

  return data;
}
