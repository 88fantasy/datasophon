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
import type { DorisDashboardSegment } from '../panelQueries';
import {
  DORIS_SEGMENT_PANEL_IDS,
  getDorisSegmentPanelIds,
  PANEL_QUERIES,
} from '../panelQueries';

export interface DorisInstantValues {
  feNodeCount: number;
  feAliveCount: number;
  beNodeCount: number;
  beAliveCount: number;
  usedCapacityBytes: number;
  totalCapacityBytes: number;
}

export interface DorisDashboardData {
  instant: DorisInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  clusters: string[];
  feInstances: string[];
  beInstances: string[];
  loading: boolean;
  error?: string;
}

interface UseDorisMonitorDashboardParams {
  variables: {
    cluster: string;
    feInstance: string;
    beInstance: string;
    interval: string;
  };
  activeSegment: DorisDashboardSegment;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

const ALL_RANGE_IDS = [
  ...DORIS_SEGMENT_PANEL_IDS.cluster,
  ...DORIS_SEGMENT_PANEL_IDS.fe,
  ...DORIS_SEGMENT_PANEL_IDS.be,
];

const EMPTY_SERIES: Record<string, TimeSeriesPoint[]> = Object.fromEntries(
  ALL_RANGE_IDS.map((id) => [id, []]),
);

export function useDorisMonitorDashboard({
  variables,
  activeSegment,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseDorisMonitorDashboardParams): DorisDashboardData {
  const [feInstances, setFeInstances] = useState<string[]>([]);
  const [beInstances, setBeInstances] = useState<string[]>([]);
  const [clusters, setClusters] = useState<string[]>([]);

  // 用 doris_fe_query_total / doris_be_memory_allocated_bytes 作为标签枚举基准
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchDorisLabels('doris_fe_query_total', clusterId),
      fetchDorisLabels('doris_be_memory_allocated_bytes', clusterId),
    ])
      .then(([feRes, beRes]) => {
        if (cancelled) return;
        setFeInstances(feRes?.data?.instances ?? []);
        setBeInstances(beRes?.data?.instances ?? []);
        // jobs 对应集群名称（otelcol 配置的 job_name=doris）
        setClusters(feRes?.data?.jobs ?? []);
      })
      .catch(() => {
        // labels 查询失败不影响面板数据，静默降级
      });
    return () => {
      cancelled = true;
    };
  }, [clusterId, refreshKey]);

  /**
   * ⚠️ 多 segment 硬约束：只传当前 segment 的 panelIds，避免一次性拉全部面板超时。
   * activeSegment 变化 → panelIds 变化 → useDorisDashboardData 重拉。
   */
  const panelIds = useMemo(
    () => getDorisSegmentPanelIds(activeSegment),
    [activeSegment],
  );

  // 不同 segment 传入不同 instance 过滤（FE/BE 分开）
  const instance =
    activeSegment === 'fe'
      ? variables.feInstance || '.+'
      : activeSegment === 'be'
        ? variables.beInstance || '.+'
        : '.+';

  const job = variables.cluster || 'doris';

  const data = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds,
    instance,
    job,
    timeRange,
    clusterId,
    refreshKey,
  });

  return {
    instant: {
      feNodeCount: data.instant['DO-A01'] ?? 0,
      feAliveCount: data.instant['DO-A02'] ?? 0,
      beNodeCount: data.instant['DO-A03'] ?? 0,
      beAliveCount: data.instant['DO-A04'] ?? 0,
      usedCapacityBytes: data.instant['DO-A05'] ?? 0,
      totalCapacityBytes: data.instant['DO-A06'] ?? 0,
    },
    series: { ...EMPTY_SERIES, ...data.series },
    clusters: clusters.length > 0 ? clusters : ['doris'],
    feInstances,
    beInstances,
    loading: data.loading,
    error: data.error,
  };
}
