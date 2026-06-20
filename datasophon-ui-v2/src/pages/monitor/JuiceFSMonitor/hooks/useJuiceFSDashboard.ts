import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import { replaceVars } from '../../_shared/charts/promql';
import { TIME_RANGE_SECONDS } from '../../_shared/panelTypes';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import { type JuiceFSDashboardVariables, PANEL_QUERIES } from '../panelQueries';

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

const ALL_PANEL_IDS = Array.from(
  { length: 17 },
  (_, i) => `J${String(i + 1).padStart(2, '0')}`,
);

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

export interface UseJuiceFSDashboardParams {
  variables: JuiceFSDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
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

  // 把 rateInterval 合入 variables，让通用 replaceVars 替换 $__rate_interval
  const effectiveVars = useMemo(
    () =>
      ({ ...variables, __rate_interval: rateInterval }) as Record<
        string,
        string
      >,
    [variables, rateInterval],
  );

  const extras = useMemo(
    () => ({
      volumeList: {
        query: 'juicefs_uptime{vol_name=~".+"}',
        kind: 'instant' as const,
      },
    }),
    [],
  );

  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) => replaceVars(promql, vars, { name: '.+' }),
    variables: effectiveVars,
    panelIds: ALL_PANEL_IDS,
    extras,
    timeRange,
    clusterId,
    refreshKey,
  });

  const volumes = useMemo(() => {
    const vec = data.extras.volumeList as PrometheusVector | undefined;
    return vec ? deriveVolumes(vec) : [];
  }, [data.extras]);

  return {
    instant: {
      uptime: data.instant.J01 ?? 0,
      dataSize: data.instant.J02 ?? 0,
      files: data.instant.J03 ?? 0,
      clientSessions: data.instant.J04 ?? 0,
      cacheHitPercent: data.instant.J05 ?? 0,
      stagingBlocks: data.instant.J06 ?? 0,
    },
    series: data.series,
    volumes,
    rateInterval,
    loading: data.loading,
    error: data.error,
  };
}
