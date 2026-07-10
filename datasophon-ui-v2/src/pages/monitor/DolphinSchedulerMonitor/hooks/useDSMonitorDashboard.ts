import { useEffect, useMemo, useState } from 'react';
import { fetchDorisLabels } from '../../_shared/dorisService';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  type DSApplication,
  type DSDashboardVariables,
  getDSSegmentPanelIds,
  PANEL_QUERIES,
} from '../panelQueries';

export const DS_APPLICATION_SERVICE_KEYWORDS: Record<DSApplication, string> = {
  'master-server': 'master',
  'worker-server': 'worker',
  'api-server': 'api',
  'alert-server': 'alert',
};

const NO_MATCHING_SERVICE = '^$';

export function resolveDSServiceName(
  application: DSApplication,
  serviceNames: string[],
): string {
  const keyword = DS_APPLICATION_SERVICE_KEYWORDS[application];
  return (
    serviceNames.find((serviceName) =>
      serviceName.toLowerCase().includes(keyword),
    ) ?? NO_MATCHING_SERVICE
  );
}

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
  const [roleJob, setRoleJob] = useState(NO_MATCHING_SERVICE);
  const [instances, setInstances] = useState<string[]>([]);

  useEffect(() => {
    setInstances([]);
    setRoleJob(NO_MATCHING_SERVICE);
    fetchDorisLabels('process_uptime_seconds', clusterId)
      .then(async (res) => {
        const serviceName = resolveDSServiceName(activeSegment, res?.data?.jobs ?? []);
        setRoleJob(serviceName);
        if (serviceName === NO_MATCHING_SERVICE) return;
        const labels = await fetchDorisLabels(
          'process_uptime_seconds',
          clusterId,
          serviceName,
        );
        setInstances(labels?.data?.instances ?? []);
      })
      .catch(() => {
        setInstances([]);
        setRoleJob(NO_MATCHING_SERVICE);
      });
  }, [activeSegment, clusterId, refreshKey]);

  const data = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds,
    instance: variables.instance,
    job: roleJob,
    timeRange,
    clusterId,
    refreshKey,
  });

  return {
    instant: data.instant,
    series: data.series,
    instances,
    loading: data.loading,
    error: data.error,
  };
}
