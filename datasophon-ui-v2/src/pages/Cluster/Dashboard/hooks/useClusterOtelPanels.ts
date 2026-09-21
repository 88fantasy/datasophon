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

import { useEffect, useRef, useState } from 'react';
import type { TimeSeriesPoint } from '../../../monitor/_shared/types';
import { useDorisDashboardData } from '../../../monitor/_shared/useDorisDashboardData';
import {
  ALL_PANEL_IDS,
  CLUSTER_DASHBOARD_JOB,
  PANEL_QUERIES,
} from '../panelQueries';

export interface UseClusterOtelPanelsParams {
  clusterId: number;
  timeRange: string;
  refreshKey: number;
}

export interface ClusterOtelPanelsData {
  cpuPercent: number;
  memoryPercent: number;
  diskPercent: number;
  cpuSeries: TimeSeriesPoint[];
  networkSeries: TimeSeriesPoint[];
  loading: boolean;
  error?: string;
  failedPanelIds: string[];
  lastSuccessAt?: number;
}

/**
 * 取多序列（各节点一条线）最新时间点的算术平均值，把逐节点 CPU 使用率折算成一个
 * 集群整体数字，供资源使用率环形图/进度条展示。沙箱五节点核数均等（16 vCPU），
 * 简单平均与按核数加权的结果一致；核数差异悬殊的集群会有轻微偏差，可接受——
 * 精确的逐节点数值就在旁边的时序图里。
 */
function averageLatestValue(points: TimeSeriesPoint[]): number {
  const latestTime = Math.max(...points.map((point) => point.time));
  if (!Number.isFinite(latestTime)) return Number.NaN;
  const latestPoints = points.filter((point) => point.time === latestTime);
  if (latestPoints.length === 0) return Number.NaN;
  return (
    latestPoints.reduce((sum, point) => sum + point.value, 0) /
    latestPoints.length
  );
}

/** 集群概览看板的 OTel 主机指标 hook，代理 useDorisDashboardData，固定 job='node'。 */
export function useClusterOtelPanels({
  clusterId,
  timeRange,
  refreshKey,
}: UseClusterOtelPanelsParams): ClusterOtelPanelsData {
  const data = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds: ALL_PANEL_IDS,
    instance: '.+',
    job: CLUSTER_DASHBOARD_JOB,
    timeRange,
    clusterId,
    refreshKey,
    skipErrorHandler: true,
  });

  const scope = `${clusterId}:${timeRange}`;
  const scopeData = useRef({
    scope,
    previousSeries: undefined as typeof data.series | undefined,
  });
  if (scopeData.current.scope !== scope) {
    scopeData.current = { scope, previousSeries: data.series };
  }
  // The shared hook retains its previous result while loading, and on total failure.
  const belongsToScope = scopeData.current.previousSeries !== data.series;
  const [lastSuccess, setLastSuccess] = useState<{
    scope: string;
    at: number;
  }>();
  useEffect(() => {
    if (
      clusterId > 0 &&
      belongsToScope &&
      !data.loading &&
      !data.error &&
      data.failedPanelIds.length === 0
    ) {
      setLastSuccess({ scope, at: Date.now() });
    }
  }, [
    data.series,
    data.instant,
    data.loading,
    data.error,
    data.failedPanelIds.length,
    scope,
    clusterId,
    belongsToScope,
  ]);
  const series = belongsToScope ? data.series : {};
  const instant = belongsToScope ? data.instant : {};

  return {
    error: data.error,
    failedPanelIds: data.failedPanelIds,
    lastSuccessAt: lastSuccess?.scope === scope ? lastSuccess.at : undefined,
    cpuPercent: averageLatestValue(series['CO-CPU'] ?? []),
    memoryPercent: instant['CO-MEM-PCT'] ?? Number.NaN,
    diskPercent: instant['CO-DISK-PCT'] ?? Number.NaN,
    cpuSeries: series['CO-CPU'] ?? [],
    networkSeries: series['CO-NET'] ?? [],
    loading: data.loading,
  };
}

export { averageLatestValue };
