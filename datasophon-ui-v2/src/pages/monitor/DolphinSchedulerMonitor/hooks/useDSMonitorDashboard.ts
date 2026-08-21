import { useEffect, useMemo, useState } from 'react';
import { parseMetricsJobs } from '../../_shared/charts/promql';
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

/**
 * 把集群全量 job 收窄到接管实例登记的 job 内；未登记 job 时原样返回。
 */
export function narrowToRegisteredJobs(
  serviceNames: string[],
  registeredJobs?: string,
): string[] {
  const registered = parseMetricsJobs(registeredJobs);
  if (registered.length === 0) return serviceNames;
  return serviceNames.filter((serviceName) => registered.includes(serviceName));
}

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
  /**
   * 接管实例登记的 metricsJob（逗号分隔）。
   *
   * DS 按 master/worker/api/alert 四个角色各自对应一个 job，所以这里不是「替换 job」，
   * 而是把角色关键字匹配的候选集收窄到本实例登记的 job 内，避免匹配到同集群其它服务。
   */
  job?: string;
  refreshKey: number;
}

export function useDSMonitorDashboard({
  variables,
  activeSegment,
  timeRange,
  clusterId = 1,
  job,
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
        const serviceName = resolveDSServiceName(
          activeSegment,
          narrowToRegisteredJobs(res?.data?.jobs ?? [], job),
        );
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
  }, [activeSegment, clusterId, job, refreshKey]);

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
