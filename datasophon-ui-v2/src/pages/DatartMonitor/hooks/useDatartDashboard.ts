import { useEffect, useState } from 'react';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import type { PrometheusVector } from '../../PrometheusMonitor/utils/promql';
import {
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  datartSeriesData,
  MOCK_APPLICATIONS,
  MOCK_HEAP_POOLS,
  MOCK_HIKARICP_POOLS,
  MOCK_INSTANCES,
  instantValues as mockInstantValues,
} from '../mock/datartMockData';
import {
  type DatartDashboardVariables,
  PANEL_QUERIES,
  replaceDatartVars,
  TIME_RANGE_SECONDS,
} from '../panelQueries';

export interface DatartInstantValues {
  uptime: number;
  heapUsedPercent: number;
  nonHeapUsedPercent: number;
  cpuUsage: number;
  hikaricpActive: number;
  errorLogsPerSecond: number;
}

export interface DatartDashboardData {
  instant: DatartInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  applications: string[];
  instances: string[];
  heapPools: string[];
  hikaricpPools: string[];
  loading: boolean;
  error?: string;
}

const DEFAULT_INSTANT: DatartInstantValues = {
  uptime: mockInstantValues.uptime,
  heapUsedPercent: mockInstantValues.heapUsedPercent,
  nonHeapUsedPercent: mockInstantValues.nonHeapUsedPercent,
  cpuUsage: mockInstantValues.cpuUsage,
  hikaricpActive: mockInstantValues.hikaricpActive,
  errorLogsPerSecond: mockInstantValues.errorLogsPerSecond,
};

const RANGE_PANEL_IDS = Array.from(
  { length: 12 },
  (_, index) => `D${String(index + 7).padStart(2, '0')}`,
);

const DEFAULT_SERIES = Object.fromEntries(
  RANGE_PANEL_IDS.map((panelId) => [panelId, datartSeriesData[panelId] ?? []]),
);

export interface UseDatartDashboardParams {
  variables: DatartDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function extractLabelOptions(
  vector: PrometheusVector,
  labelKey: string,
): string[] {
  const values = new Set<string>();

  for (const item of vector.result) {
    const value = item.metric[labelKey];
    if (value) values.add(value);
  }

  return [...values].sort();
}

function withFallback(values: string[], fallback: string[]): string[] {
  return values.length > 0 ? values : fallback;
}

export function useDatartDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDatartDashboardParams): DatartDashboardData {
  const [data, setData] = useState<DatartDashboardData>({
    instant: DEFAULT_INSTANT,
    series: DEFAULT_SERIES,
    applications: MOCK_APPLICATIONS,
    instances: MOCK_INSTANCES,
    heapPools: MOCK_HEAP_POOLS,
    hikaricpPools: MOCK_HIKARICP_POOLS,
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
            query: replaceDatartVars(def.promql, variables),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        async function panelSeries(panelId: string) {
          const def = PANEL_QUERIES[panelId];

          if (def.type === 'range') {
            const res = await queryRange({
              query: replaceDatartVars(def.promql, variables),
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
                  query: replaceDatartVars(promql, variables),
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
          d01,
          d02,
          d03,
          d04,
          d05,
          d06,
          applicationVector,
          instanceVector,
          heapPoolVector,
          hikaricpVector,
          ...rangeValues
        ] = await Promise.all([
          instant('D01'),
          instant('D02'),
          instant('D03'),
          instant('D04'),
          instant('D05'),
          instant('D06'),
          queryInstant({
            query: 'process_uptime_seconds',
            time: end,
            clusterId,
          }).then(
            (res) => res?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          queryInstant({
            query: `process_uptime_seconds{application="${variables.application}"}`,
            time: end,
            clusterId,
          }).then(
            (res) => res?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          queryInstant({
            query: `jvm_memory_used_bytes{application="${variables.application}", area="heap"}`,
            time: end,
            clusterId,
          }).then(
            (res) => res?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          queryInstant({
            query: `hikaricp_connections{application="${variables.application}"}`,
            time: end,
            clusterId,
          }).then(
            (res) => res?.data ?? { resultType: 'vector' as const, result: [] },
          ),
          ...RANGE_PANEL_IDS.map((panelId) => panelSeries(panelId)),
        ]);

        if (cancelled) return;

        const applications = extractLabelOptions(
          applicationVector,
          'application',
        );
        const instances = extractLabelOptions(instanceVector, 'instance');
        const heapPools = extractLabelOptions(heapPoolVector, 'id');
        const hikaricpPools = extractLabelOptions(hikaricpVector, 'pool');

        setData({
          instant: {
            uptime: d01 ? vectorToScalar(d01) : DEFAULT_INSTANT.uptime,
            heapUsedPercent: d02
              ? vectorToScalar(d02)
              : DEFAULT_INSTANT.heapUsedPercent,
            nonHeapUsedPercent: d03
              ? vectorToScalar(d03)
              : DEFAULT_INSTANT.nonHeapUsedPercent,
            cpuUsage: d04 ? vectorToScalar(d04) : DEFAULT_INSTANT.cpuUsage,
            hikaricpActive: d05
              ? vectorToScalar(d05)
              : DEFAULT_INSTANT.hikaricpActive,
            errorLogsPerSecond: d06
              ? vectorToScalar(d06)
              : DEFAULT_INSTANT.errorLogsPerSecond,
          },
          series: Object.fromEntries(
            RANGE_PANEL_IDS.map((panelId, index) => [
              panelId,
              rangeValues[index].length > 0
                ? rangeValues[index]
                : (datartSeriesData[panelId] ?? []),
            ]),
          ),
          applications: withFallback(applications, MOCK_APPLICATIONS),
          instances: withFallback(instances, MOCK_INSTANCES),
          heapPools: withFallback(heapPools, MOCK_HEAP_POOLS),
          hikaricpPools: withFallback(hikaricpPools, MOCK_HIKARICP_POOLS),
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
