/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { useEffect, useMemo, useState } from 'react';
import { fetchDorisLabels } from '../../_shared/dorisService';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  CLUSTER_PANEL_IDS,
  getSeaTunnelSegmentPanelIds,
  PANEL_QUERIES,
  SEATUNNEL_JOB_BY_SEGMENT,
  type SeaTunnelDashboardSegment,
} from '../panelQueries';

export interface SeaTunnelDashboardData {
  instant: Record<string, number>;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
  failedPanelIds: string[];
}

export interface UseSeaTunnelDashboardParams {
  activeSegment: SeaTunnelDashboardSegment;
  instance: string;
  timeRange: string;
  clusterId: number;
  refreshKey: number;
}

export function useSeaTunnelDashboard({
  activeSegment,
  instance,
  timeRange,
  clusterId,
  refreshKey,
}: UseSeaTunnelDashboardParams): SeaTunnelDashboardData {
  const [instances, setInstances] = useState<string[]>([]);
  const job = SEATUNNEL_JOB_BY_SEGMENT[activeSegment];

  useEffect(() => {
    let cancelled = false;
    setInstances([]);
    if (clusterId <= 0)
      return () => {
        cancelled = true;
      };

    fetchDorisLabels('node_count', clusterId, job)
      .then((res) => {
        if (!cancelled) setInstances(res?.data?.instances ?? []);
      })
      .catch(() => {
        if (!cancelled) setInstances([]);
      });

    return () => {
      cancelled = true;
    };
  }, [clusterId, job, refreshKey]);

  const segmentPanelIds = useMemo(
    () => getSeaTunnelSegmentPanelIds(activeSegment),
    [activeSegment],
  );
  const clusterPanelIds = useMemo(
    () => segmentPanelIds.filter((id) => CLUSTER_PANEL_IDS.includes(id)),
    [segmentPanelIds],
  );
  const instancePanelIds = useMemo(
    () => segmentPanelIds.filter((id) => !CLUSTER_PANEL_IDS.includes(id)),
    [segmentPanelIds],
  );

  const instanceData = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds: instancePanelIds,
    instance,
    job,
    timeRange,
    clusterId,
    refreshKey,
  });
  const clusterData = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds: clusterPanelIds,
    instance: '.+',
    job: SEATUNNEL_JOB_BY_SEGMENT.master,
    timeRange,
    clusterId,
    refreshKey,
  });

  return {
    instant: { ...instanceData.instant, ...clusterData.instant },
    series: { ...instanceData.series, ...clusterData.series },
    instances,
    loading: instanceData.loading || clusterData.loading,
    error: instanceData.error ?? clusterData.error,
    failedPanelIds: [
      ...new Set([
        ...instanceData.failedPanelIds,
        ...clusterData.failedPanelIds,
      ]),
    ].sort(),
  };
}
