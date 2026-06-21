import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import {
  type KyuubiDashboardVariables,
  PANEL_QUERIES,
  replaceKyuubiVars,
} from '../panelQueries';

export interface KyuubiInstantValues {
  instances: number;
  uptime: number;
  connectionOpened: number;
  engineTotal: number;
  execPoolThreads: number;
  operationErrorRate: number;
}

export interface KyuubiDashboardData {
  instant: KyuubiInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  connTypes: string[];
  opTypes: string[];
  trendInterval: string;
  loading: boolean;
  error?: string;
}

export interface UseKyuubiDashboardParams {
  variables: KyuubiDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export const KYUUBI_CONN_TYPES = [
  'connection_total_INTERACTIVE',
  'connection_total_BATCH',
];

export const KYUUBI_OP_TYPES = [
  'ExecuteStatement',
  'LaunchEngine',
  'GetSchemas',
  'GetTables',
  'GetColumns',
  'GetFunctions',
  'GetCatalogs',
  'GetTypeInfo',
];

const INSTANT_IDS = ['KY01', 'KY02', 'KY03', 'KY04', 'KY05', 'KY06'];
const SERIES_IDS = Array.from(
  { length: 10 },
  (_, i) => `KY${String(i + 7).padStart(2, '0')}`,
);
const ALL_PANEL_IDS = [...INSTANT_IDS, ...SERIES_IDS];

function trendIntervalForRange(timeRange: string): string {
  if (timeRange === '5m' || timeRange === '15m') return '1m';
  if (timeRange === '6h' || timeRange === '24h') return '15m';
  if (timeRange === '7d') return '1h';
  return '5m';
}

function deriveKyuubiInstances(vector: PrometheusVector): string[] {
  return [
    ...new Set(
      vector.result
        .map((item) => item.metric.instance)
        .filter((v): v is string => Boolean(v)),
    ),
  ];
}

export function useKyuubiDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseKyuubiDashboardParams): KyuubiDashboardData {
  const trendInterval = useMemo(
    () => trendIntervalForRange(timeRange),
    [timeRange],
  );

  const effectiveVariables = useMemo(
    () => ({ ...variables, trendInterval }),
    [variables, trendInterval],
  );

  // extras 的 query 必须预先用 replaceKyuubiVars 展开，因为 DashboardData 只存储原始 string
  const extras = useMemo(
    () => ({
      instanceList: {
        query: replaceKyuubiVars(
          'kyuubi_jvm_uptime{$baseFilter}',
          effectiveVariables,
        ),
        kind: 'instant' as const,
      },
    }),
    [effectiveVariables],
  );

  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    // replaceKyuubiVars 处理 ${connType}/${opType} 等特殊语法，通用 replaceVars 不支持
    replaceVars: (promql, vars) =>
      replaceKyuubiVars(promql, vars as Partial<KyuubiDashboardVariables>),
    variables: effectiveVariables as Record<string, string>,
    panelIds: ALL_PANEL_IDS,
    extras,
    timeRange,
    clusterId,
    refreshKey,
  });

  const discoveredInstances = useMemo(() => {
    const vec = data.extras.instanceList as PrometheusVector | undefined;
    return vec ? deriveKyuubiInstances(vec) : [];
  }, [data.extras]);

  return {
    instant: {
      instances: data.instant.KY01 ?? 0,
      uptime: data.instant.KY02 ?? 0,
      connectionOpened: data.instant.KY03 ?? 0,
      engineTotal: data.instant.KY04 ?? 0,
      execPoolThreads: data.instant.KY05 ?? 0,
      operationErrorRate: data.instant.KY06 ?? 0,
    },
    series: data.series,
    instances: discoveredInstances,
    connTypes: KYUUBI_CONN_TYPES,
    opTypes: KYUUBI_OP_TYPES,
    trendInterval,
    loading: data.loading,
    error: data.error,
  };
}
