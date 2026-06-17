import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import { deriveInstancesAndJobs } from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import {
  DORIS_RANGE_PANEL_IDS,
  type DorisDashboardSegment,
  type DorisDashboardVariables,
  getDorisSegmentPanelIds,
  PANEL_QUERIES,
  replaceDorisVars,
} from '../panelQueries';

export interface DorisInstantValues {
  feNodeCount: number;
  feAliveCount: number;
  beNodeCount: number;
  beAliveCount: number;
  usedCapacityBytes: number;
  totalCapacityBytes: number;
}

export interface DorisDashboardData {
  instant: DorisInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  clusters: string[];
  feInstances: string[];
  beInstances: string[];
  loading: boolean;
  error?: string;
}

interface UseDorisMonitorDashboardParams {
  variables: DorisDashboardVariables;
  activeSegment: DorisDashboardSegment;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

const EMPTY_SERIES: Record<string, TimeSeriesPoint[]> = Object.fromEntries(
  DORIS_RANGE_PANEL_IDS.map((id) => [id, []]),
);

export function useDorisMonitorDashboard({
  variables,
  activeSegment,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDorisMonitorDashboardParams): DorisDashboardData {
  /**
   * ⚠️ 多 segment 硬约束：只传当前 segment 的 panelIds，避免一次性拉全部面板超时。
   * activeSegment 变化 → panelIds 变化 → useDashboardData 重拉。
   */
  const panelIds = useMemo(
    () => getDorisSegmentPanelIds(activeSegment),
    [activeSegment],
  );

  const extras = useMemo(
    () => ({
      clustersVec: { query: 'up{group="fe"}', kind: 'instant' as const },
      feUp: {
        query: `up{group="fe", job="${variables.cluster || 'doris'}"}`,
        kind: 'instant' as const,
      },
      beUp: {
        query: `up{group="be", job="${variables.cluster || 'doris'}"}`,
        kind: 'instant' as const,
      },
    }),
    [variables.cluster],
  );

  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) =>
      replaceDorisVars(promql, vars as Partial<DorisDashboardVariables>),
    variables: variables as unknown as Record<string, string>,
    panelIds,
    extras,
    timeRange,
    clusterId,
    refreshKey,
  });

  const { clusters, feInstances, beInstances } = useMemo(() => {
    const clustersVec = data.extras.clustersVec as PrometheusVector | undefined;
    const feUpVec = data.extras.feUp as PrometheusVector | undefined;
    const beUpVec = data.extras.beUp as PrometheusVector | undefined;
    return {
      clusters: clustersVec ? deriveInstancesAndJobs(clustersVec).jobs : [],
      feInstances: feUpVec ? deriveInstancesAndJobs(feUpVec).instances : [],
      beInstances: beUpVec ? deriveInstancesAndJobs(beUpVec).instances : [],
    };
  }, [data.extras]);

  return {
    instant: {
      feNodeCount: data.instant['DO-A01'] ?? 0,
      feAliveCount: data.instant['DO-A02'] ?? 0,
      beNodeCount: data.instant['DO-A03'] ?? 0,
      beAliveCount: data.instant['DO-A04'] ?? 0,
      usedCapacityBytes: data.instant['DO-A05'] ?? 0,
      totalCapacityBytes: data.instant['DO-A06'] ?? 0,
    },
    series: { ...EMPTY_SERIES, ...data.series },
    clusters,
    feInstances,
    beInstances,
    loading: data.loading,
    error: data.error,
  };
}
