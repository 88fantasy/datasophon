import { useEffect, useMemo, useState } from 'react';
import { fetchDorisLabels } from '../../_shared/dorisService';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  KYUUBI_CONN_TYPES,
  KYUUBI_OP_TYPES,
  buildKyuubiPanelQueries,
  type KyuubiDashboardVariables,
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
  jobs: string[];
  connTypes: string[];
  opTypes: string[];
  trendInterval: string;
  loading: boolean;
  error?: string;
}

const ALL_PANEL_IDS = Array.from(
  { length: 16 },
  (_, index) => `KY${String(index + 1).padStart(2, '0')}`,
);

function trendIntervalForRange(timeRange: string): '1m' | '5m' | '15m' | '1h' {
  if (timeRange === '5m' || timeRange === '15m') return '1m';
  if (timeRange === '6h' || timeRange === '24h') return '15m';
  if (timeRange === '7d') return '1h';
  return '5m';
}

export interface UseKyuubiDashboardParams {
  variables: KyuubiDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useKyuubiDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseKyuubiDashboardParams): KyuubiDashboardData {
  const [labels, setLabels] = useState<{ instances: string[]; jobs: string[] }>(
    {
      instances: [],
      jobs: [],
    },
  );

  useEffect(() => {
    fetchDorisLabels('kyuubi_jvm_uptime', clusterId)
      .then((res) => {
        if (res?.data) {
          setLabels({
            instances: [...res.data.instances].sort(),
            jobs: [...res.data.jobs].sort(),
          });
        }
      })
      .catch(() => {
        // labels 查询失败不影响已选数据源的面板查询。
      });
  }, [clusterId, refreshKey]);

  const trendInterval = useMemo(
    () => trendIntervalForRange(timeRange),
    [timeRange],
  );

  const panelDescriptors = useMemo(
    () =>
      buildKyuubiPanelQueries(
        variables.connType,
        variables.opType,
        trendInterval,
      ),
    [trendInterval, variables.connType, variables.opType],
  );

  const data = useDorisDashboardData({
    panelDescriptors,
    panelIds: ALL_PANEL_IDS,
    instance: variables.instance,
    job: variables.job,
    timeRange,
    clusterId,
    refreshKey,
  });

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
    instances: labels.instances,
    jobs: labels.jobs,
    connTypes: KYUUBI_CONN_TYPES,
    opTypes: KYUUBI_OP_TYPES,
    trendInterval,
    loading: data.loading,
    error: data.error,
  };
}
