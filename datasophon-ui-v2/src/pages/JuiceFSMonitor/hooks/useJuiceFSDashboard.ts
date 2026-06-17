import { useEffect, useMemo, useState } from 'react';
import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';
import { queryInstant, queryRange } from '../../PrometheusMonitor/service';
import type { PrometheusVector } from '../../PrometheusMonitor/utils/promql';
import {
  matrixToSeries,
  mergeNamedSeries,
  vectorToScalar,
} from '../../PrometheusMonitor/utils/promql';
import {
  juiceFSSeriesData,
  MOCK_VOLUMES,
  instantValues as mockInstantValues,
} from '../mock/juicefsMockData';
import {
  type JuiceFSDashboardVariables,
  PANEL_QUERIES,
  replaceJuiceFSVars,
  TIME_RANGE_SECONDS,
} from '../panelQueries';

export interface JuiceFSInstantValues {
  uptime: number;
  dataSize: number;
  files: number;
  clientSessions: number;
  cacheHitPercent: number;
  stagingBlocks: number;
}

export interface JuiceFSDashboardData {
  instant: JuiceFSInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  volumes: string[];
  loading: boolean;
  rateInterval: string;
  error?: string;
}

const DEFAULT_INSTANT: JuiceFSInstantValues = {
  uptime: mockInstantValues.uptime,
  dataSize: mockInstantValues.dataSize,
  files: mockInstantValues.files,
  clientSessions: mockInstantValues.clientSessions,
  cacheHitPercent: mockInstantValues.cacheHitPercent,
  stagingBlocks: mockInstantValues.stagingBlocks,
};

const RANGE_PANEL_IDS = Array.from(
  { length: 11 },
  (_, index) => `J${String(index + 7).padStart(2, '0')}`,
);

const DEFAULT_SERIES = Object.fromEntries(
  RANGE_PANEL_IDS.map((panelId) => [panelId, juiceFSSeriesData[panelId] ?? []]),
);

export interface UseJuiceFSDashboardParams {
  variables: JuiceFSDashboardVariables;
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

function deriveVolumes(vector: PrometheusVector): string[] {
  const volumes = new Set<string>();
  for (const item of vector.result) {
    if (item.metric.vol_name) volumes.add(item.metric.vol_name);
  }
  return [...volumes];
}

export function useJuiceFSDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseJuiceFSDashboardParams): JuiceFSDashboardData {
  const rateInterval = useMemo(() => {
    const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
    return calcRateInterval(rangeSeconds);
  }, [timeRange]);

  const [data, setData] = useState<JuiceFSDashboardData>({
    instant: DEFAULT_INSTANT,
    series: DEFAULT_SERIES,
    volumes: MOCK_VOLUMES,
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
            query: replaceJuiceFSVars(def.promql, variables, rateInterval),
            time: end,
            clusterId,
          });
          return res?.data ?? null;
        }

        async function panelSeries(panelId: string) {
          const def = PANEL_QUERIES[panelId];

          if (def.type === 'range') {
            const res = await queryRange({
              query: replaceJuiceFSVars(def.promql, variables, rateInterval),
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
                  query: replaceJuiceFSVars(promql, variables, rateInterval),
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

        const [j01, j02, j03, j04, j05, j06, volumeVector, ...rangeValues] =
          await Promise.all([
            instant('J01'),
            instant('J02'),
            instant('J03'),
            instant('J04'),
            instant('J05'),
            instant('J06'),
            queryInstant({
              query: 'juicefs_uptime',
              time: end,
              clusterId,
            }).then(
              (res) =>
                res?.data ?? { resultType: 'vector' as const, result: [] },
            ),
            ...RANGE_PANEL_IDS.map((panelId) => panelSeries(panelId)),
          ]);

        if (cancelled) return;

        const volumes = deriveVolumes(volumeVector);

        setData({
          instant: {
            uptime: j01 ? vectorToScalar(j01) : DEFAULT_INSTANT.uptime,
            dataSize: j02 ? vectorToScalar(j02) : DEFAULT_INSTANT.dataSize,
            files: j03 ? vectorToScalar(j03) : DEFAULT_INSTANT.files,
            clientSessions: j04
              ? vectorToScalar(j04)
              : DEFAULT_INSTANT.clientSessions,
            cacheHitPercent: j05
              ? vectorToScalar(j05)
              : DEFAULT_INSTANT.cacheHitPercent,
            stagingBlocks: j06
              ? vectorToScalar(j06)
              : DEFAULT_INSTANT.stagingBlocks,
          },
          series: Object.fromEntries(
            RANGE_PANEL_IDS.map((panelId, index) => [
              panelId,
              rangeValues[index].length > 0
                ? rangeValues[index]
                : DEFAULT_SERIES[panelId],
            ]),
          ),
          volumes: volumes.length > 0 ? volumes : MOCK_VOLUMES,
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
