import { useMemo } from 'react';
import type { PrometheusVector } from '../../_shared/charts/promql';
import { deriveInstancesAndJobs } from '../../_shared/charts/promql';
import { TIME_RANGE_SECONDS } from '../../_shared/panelTypes';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDashboardData } from '../../_shared/useDashboardData';
import {
  type MySQLDashboardVariables,
  PANEL_QUERIES,
  replaceMySQLVars,
} from '../panelQueries';

export interface MySQLInstantValues {
  uptime: number;
  currentQps: number;
  connectionsUsedPercent: number;
  innodbBufferPool: number;
  slowQueriesPerSecond: number;
  abortedConnectionsPerSecond: number;
}

export interface MySQLDashboardData {
  instant: MySQLInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  jobs: string[];
  loading: boolean;
  rateInterval: string;
  error?: string;
}

const ALL_PANEL_IDS = Array.from(
  { length: 17 },
  (_, i) => `M${String(i + 1).padStart(2, '0')}`,
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

const EXTRAS = { up: { query: 'mysql_up', kind: 'instant' as const } };

export interface UseMySQLDashboardParams {
  variables: MySQLDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useMySQLDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseMySQLDashboardParams): MySQLDashboardData {
  const rateInterval = useMemo(() => {
    const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
    return calcRateInterval(rangeSeconds);
  }, [timeRange]);

  const data = useDashboardData({
    panelQueries: PANEL_QUERIES,
    // replaceMySQLVars 处理 [$__interval] 特殊语法，通用 replaceVars 不支持
    replaceVars: (promql, vars) =>
      replaceMySQLVars(
        promql,
        vars as Partial<MySQLDashboardVariables>,
        rateInterval,
      ),
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
      uptime: data.instant.M01 ?? 0,
      currentQps: data.instant.M02 ?? 0,
      connectionsUsedPercent: data.instant.M03 ?? 0,
      innodbBufferPool: data.instant.M04 ?? 0,
      slowQueriesPerSecond: data.instant.M05 ?? 0,
      abortedConnectionsPerSecond: data.instant.M06 ?? 0,
    },
    series: data.series,
    instances,
    jobs,
    rateInterval,
    loading: data.loading,
    error: data.error,
  };
}
