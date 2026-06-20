import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import {
  deriveInstancesAndJobs,
  replaceVars,
} from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import { type NexusDashboardVariables, PANEL_QUERIES } from '../panelQueries';

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

const ALL_PANEL_IDS = Array.from(
  { length: 18 },
  (_, i) => `N${String(i + 1).padStart(2, '0')}`,
);

const EXTRAS = {
  up: {
    query: 'jvm_vm_uptime{instance=~".+",job=~".+"}',
    kind: 'instant' as const,
  },
};

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
  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) =>
      replaceVars(promql, vars, { instance: '.+', job: '.+' }),
    variables: variables as unknown as Record<string, string>,
    panelIds: ALL_PANEL_IDS,
    extras: EXTRAS,
    timeRange,
    clusterId,
    refreshKey,
  });

  const { instances, jobs } = useMemo(() => {
    const upVector = data.extras.up as PrometheusVector | undefined;
    if (!upVector) return { instances: [], jobs: [] };
    return deriveInstancesAndJobs(upVector);
  }, [data.extras]);

  return {
    instant: {
      uptime: data.instant.N01 ?? 0,
      heapRatio: data.instant.N02 ?? 0,
      fdRatio: data.instant.N03 ?? 0,
      readonlyEnabled: data.instant.N04 ?? 0,
      jvmThreads: data.instant.N05 ?? 0,
      deadlockThreads: data.instant.N06 ?? 0,
    },
    series: data.series,
    instances,
    jobs,
    loading: data.loading,
    error: data.error,
  };
}
