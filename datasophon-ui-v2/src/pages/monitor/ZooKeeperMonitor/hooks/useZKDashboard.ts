import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import {
  deriveInstancesAndJobs,
  replaceVars,
} from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import { PANEL_QUERIES, type ZKDashboardVariables } from '../panelQueries';

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

const ALL_PANEL_IDS = Array.from(
  { length: 23 },
  (_, i) => `Z${String(i + 1).padStart(2, '0')}`,
);

const EXTRAS = {
  up: { query: 'up', kind: 'instant' as const },
};

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
      quorumSize: data.instant.Z01 ?? 0,
      leaderUptime: data.instant.Z02 ?? 0,
      jvmThreads: data.instant.Z03 ?? 0,
      deadlockedThreads: data.instant.Z04 ?? 0,
      aliveConnections: data.instant.Z05 ?? 0,
      openFileDescriptors: data.instant.Z06 ?? 0,
    },
    series: data.series,
    instances,
    jobs,
    loading: data.loading,
    error: data.error,
  };
}
