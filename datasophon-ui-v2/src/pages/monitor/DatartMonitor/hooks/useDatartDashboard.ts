import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import {
  type DatartDashboardVariables,
  PANEL_QUERIES,
  replaceDatartVars,
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

export interface UseDatartDashboardParams {
  variables: DatartDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

const INSTANT_IDS = ['D01', 'D02', 'D03', 'D04', 'D05', 'D06'];
const SERIES_IDS = Array.from(
  { length: 12 },
  (_, i) => `D${String(i + 7).padStart(2, '0')}`,
);
const ALL_PANEL_IDS = [...INSTANT_IDS, ...SERIES_IDS];

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

export function useDatartDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDatartDashboardParams): DatartDashboardData {
  const extras = useMemo(
    () => ({
      appList: { query: 'process_uptime_seconds', kind: 'instant' as const },
      instanceList: {
        query: `process_uptime_seconds{application="${variables.application}"}`,
        kind: 'instant' as const,
      },
      heapPoolList: {
        query: `jvm_memory_used_bytes{application="${variables.application}", area="heap"}`,
        kind: 'instant' as const,
      },
      hikaricpList: {
        query: `hikaricp_connections{application="${variables.application}"}`,
        kind: 'instant' as const,
      },
    }),
    [variables.application],
  );

  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    replaceVars: (promql, vars) =>
      replaceDatartVars(promql, vars as Partial<DatartDashboardVariables>),
    variables: variables as unknown as Record<string, string>,
    panelIds: ALL_PANEL_IDS,
    extras,
    timeRange,
    clusterId,
    refreshKey,
  });

  const { applications, instances, heapPools, hikaricpPools } = useMemo(() => {
    const appVec = data.extras.appList as PrometheusVector | undefined;
    const instVec = data.extras.instanceList as PrometheusVector | undefined;
    const heapVec = data.extras.heapPoolList as PrometheusVector | undefined;
    const hikVec = data.extras.hikaricpList as PrometheusVector | undefined;
    return {
      applications: appVec ? extractLabelOptions(appVec, 'application') : [],
      instances: instVec ? extractLabelOptions(instVec, 'instance') : [],
      heapPools: heapVec ? extractLabelOptions(heapVec, 'id') : [],
      hikaricpPools: hikVec ? extractLabelOptions(hikVec, 'pool') : [],
    };
  }, [data.extras]);

  return {
    instant: {
      uptime: data.instant.D01 ?? 0,
      heapUsedPercent: data.instant.D02 ?? 0,
      nonHeapUsedPercent: data.instant.D03 ?? 0,
      cpuUsage: data.instant.D04 ?? 0,
      hikaricpActive: data.instant.D05 ?? 0,
      errorLogsPerSecond: data.instant.D06 ?? 0,
    },
    series: data.series,
    applications,
    instances,
    heapPools,
    hikaricpPools,
    loading: data.loading,
    error: data.error,
  };
}
