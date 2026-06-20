import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import { deriveInstancesAndJobs } from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import {
  PANEL_QUERIES,
  replaceValkeyVars,
  type ValkeyDashboardVariables,
} from '../panelQueries';

export interface ValkeyInstantValues {
  maxUptime: number;
  clients: number;
  memoryUsagePct: number;
  memoryMaxBytes: number;
  cacheHitPct: number;
}

export interface ValkeyDashboardData {
  instant: ValkeyInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

const ALL_PANEL_IDS = [
  'V01', 'V02', 'V03', 'V03_max', 'V04',
  'V05', 'V06', 'V07', 'V08',
  'V09', 'V10',
  'V11', 'V12', 'V13', 'V14',
];

const EXTRAS = {
  up: { query: 'redis_up', kind: 'instant' as const },
};

export interface UseValkeyDashboardParams {
  variables: ValkeyDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useValkeyDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseValkeyDashboardParams): ValkeyDashboardData {
  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) => replaceValkeyVars(promql, vars),
    variables: variables as unknown as Record<string, string>,
    panelIds: ALL_PANEL_IDS,
    extras: EXTRAS,
    timeRange,
    clusterId,
    refreshKey,
  });

  const { instances } = useMemo(() => {
    const upVector = data.extras.up as PrometheusVector | undefined;
    if (!upVector) return { instances: [] };
    return deriveInstancesAndJobs(upVector);
  }, [data.extras]);

  const memoryMaxBytes = data.instant.V03_max ?? 0;

  // V09 series: hide Max line when maxmemory is not configured (=0)
  const memorySeries: TimeSeriesPoint[] = useMemo(() => {
    const raw = data.series.V09 ?? [];
    if (memoryMaxBytes <= 0) {
      return raw.filter((p) => p.series !== 'Max');
    }
    return raw;
  }, [data.series.V09, memoryMaxBytes]);

  // V14 Rejected Connections — fall back to V13 Evicted series if metric absent
  const rejectedOrEvictedSeries: TimeSeriesPoint[] = useMemo(() => {
    const v14 = data.series.V14 ?? [];
    if (v14.length > 0) return v14;
    // fallback: use Evicted series from V13 as the primary error signal
    return (data.series.V13 ?? []).filter((p) => p.series === 'Evicted');
  }, [data.series.V14, data.series.V13]);

  return {
    instant: {
      maxUptime: data.instant.V01 ?? 0,
      clients: data.instant.V02 ?? 0,
      memoryUsagePct: memoryMaxBytes <= 0 ? -1 : (data.instant.V03 ?? 0),
      memoryMaxBytes,
      cacheHitPct: Number.isNaN(data.instant.V04) ? 0 : (data.instant.V04 ?? 0),
    },
    series: {
      ...data.series,
      V09: memorySeries,
      V14: rejectedOrEvictedSeries,
    },
    instances,
    loading: data.loading,
    error: data.error,
  };
}
