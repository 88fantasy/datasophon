import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import {
  deriveInstancesAndJobs,
  replaceVars,
} from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import { type DSDashboardVariables, PANEL_QUERIES } from '../panelQueries';

export interface DSInstantValues {
  taskTotal: number;
  taskSuccessRate: number;
  quartzJobTotal: number;
  quartzJobSuccessRate: number;
  uptime: number;
  heapUsedPercent: number;
  nonHeapUsedPercent: number;
}

export interface DSDashboardData {
  instant: DSInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

// instant 面板 ID → 命名字段映射
const INSTANT_MAP: Record<keyof DSInstantValues, string> = {
  taskTotal: 'D-B01',
  taskSuccessRate: 'D-B02',
  quartzJobTotal: 'D-B03',
  quartzJobSuccessRate: 'D-B04',
  uptime: 'D-C01',
  heapUsedPercent: 'D-C02',
  nonHeapUsedPercent: 'D-C03',
};

const SERIES_IDS = [
  'D-A01',
  'D-A02',
  'D-A03',
  'D-A04',
  'D-A05',
  'D-A06',
  'D-B05',
  'D-B06',
  'D-B07',
  'D-B08',
  'D-B09',
  'D-B10',
  'D-B11',
  'D-B12',
  'D-B13',
  'D-C04',
  'D-C05',
  'D-C06',
  'D-C07',
  'D-C08',
  'D-C09',
  'D-C10',
  'D-C11',
  'D-C12',
  'D-C13',
];

const ALL_PANEL_IDS = [...Object.values(INSTANT_MAP), ...SERIES_IDS];

export interface UseDSMonitorDashboardParams {
  variables: DSDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useDSMonitorDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDSMonitorDashboardParams): DSDashboardData {
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
    panelIds: ALL_PANEL_IDS,
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
    instant: {
      taskTotal: data.instant['D-B01'] ?? 0,
      taskSuccessRate: data.instant['D-B02'] ?? 0,
      quartzJobTotal: data.instant['D-B03'] ?? 0,
      quartzJobSuccessRate: data.instant['D-B04'] ?? 0,
      uptime: data.instant['D-C01'] ?? 0,
      heapUsedPercent: data.instant['D-C02'] ?? 0,
      nonHeapUsedPercent: data.instant['D-C03'] ?? 0,
    },
    series: data.series,
    instances,
    loading: data.loading,
    error: data.error,
  };
}
