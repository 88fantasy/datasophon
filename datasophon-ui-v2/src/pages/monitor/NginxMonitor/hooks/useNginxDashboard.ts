import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import { deriveInstancesAndJobs } from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import { PANEL_QUERIES, replaceNginxVars, type NginxDashboardVariables } from '../panelQueries';

export interface NginxInstantValues {
  status: number;
  activeConnections: number;
  droppedConnections: number;
}

export interface NginxDashboardData {
  instant: NginxInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

const ALL_PANEL_IDS = ['N01', 'N02', 'N03', 'N04', 'N05', 'N06'];

const EXTRAS = {
  up: { query: 'nginx_up', kind: 'instant' as const },
};

export interface UseNginxDashboardParams {
  variables: NginxDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useNginxDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseNginxDashboardParams): NginxDashboardData {
  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) => replaceNginxVars(promql, vars),
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

  return {
    instant: {
      status: data.instant.N01 ?? 0,
      activeConnections: data.instant.N02 ?? 0,
      droppedConnections: data.instant.N03 ?? 0,
    },
    series: data.series,
    instances,
    loading: data.loading,
    error: data.error,
  };
}
