import { useEffect, useState } from 'react';
import { fetchDorisLabels } from '../../_shared/dorisService';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
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

const ALL_PANEL_IDS = Object.keys(PANEL_QUERIES);

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
  const [labels, setLabels] = useState<{ instances: string[]; jobs: string[] }>(
    { instances: [], jobs: [] },
  );

  useEffect(() => {
    fetchDorisLabels('jvm_threads_current', clusterId)
      .then((res) => {
        if (res?.data) setLabels(res.data);
      })
      .catch(() => {
        // labels 查询失败不影响面板数据，静默降级
      });
  }, [clusterId, refreshKey]);

  const data = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds: ALL_PANEL_IDS,
    instance: variables.instance,
    job: variables.job,
    timeRange,
    clusterId,
    refreshKey,
  });

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
    instances: labels.instances,
    jobs: labels.jobs,
    loading: data.loading,
    error: data.error,
  };
}
