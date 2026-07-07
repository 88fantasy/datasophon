import type { TimeRange } from '../../../monitor/_shared/DashboardToolbar';
import { useDorisDashboardData } from '../../../monitor/_shared/useDorisDashboardData';
import { COLLECTOR_PANEL_IDS, COLLECTOR_PANEL_QUERIES } from '../panelQueries';

const ALL_PANEL_IDS = [...COLLECTOR_PANEL_IDS];

interface UseCollectorDashboardParams {
  clusterId: number;
  timeRange: TimeRange;
  refreshKey: number;
}

export function useCollectorDashboard({
  clusterId,
  timeRange,
  refreshKey,
}: UseCollectorDashboardParams) {
  return useDorisDashboardData({
    panelDescriptors: COLLECTOR_PANEL_QUERIES,
    panelIds: ALL_PANEL_IDS,
    instance: '.+',
    job: '.+',
    timeRange,
    clusterId,
    refreshKey,
  });
}
