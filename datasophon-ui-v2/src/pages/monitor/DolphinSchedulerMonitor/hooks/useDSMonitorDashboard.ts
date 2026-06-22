import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import {
  deriveInstancesAndJobs,
  replaceVars,
} from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import {
  type DSApplication,
  type DSDashboardVariables,
  getDSSegmentPanelIds,
  PANEL_QUERIES,
} from '../panelQueries';

export interface DSDashboardData {
  instant: Record<string, number>;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

interface UseDSMonitorDashboardParams {
  variables: DSDashboardVariables;
  /** 当前激活 Tab，决定拉取哪些面板（多 segment 硬约束：只拉当前 segment）。 */
  activeSegment: DSApplication;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useDSMonitorDashboard({
  variables,
  activeSegment,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDSMonitorDashboardParams): DSDashboardData {
  const panelIds = useMemo(
    () => getDSSegmentPanelIds(activeSegment),
    [activeSegment],
  );

  const extras = useMemo(
    () => ({
      up: {
        query: `up{application="${variables.application || 'master-server'}"}`,
        kind: 'instant' as const,
      },
    }),
    [variables.application],
  );

  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) =>
      replaceVars(promql, vars, {
        application: 'master-server',
        instance: '.+',
      }),
    variables: variables as unknown as Record<string, string>,
    panelIds,
    extras,
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
    instant: data.instant,
    series: data.series,
    instances,
    loading: data.loading,
    error: data.error,
  };
}
