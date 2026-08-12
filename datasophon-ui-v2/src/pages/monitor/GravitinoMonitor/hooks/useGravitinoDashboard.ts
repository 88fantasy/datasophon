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

import { useEffect, useState } from 'react';
import { fetchDorisLabels } from '../../_shared/dorisService';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  ALL_PANEL_IDS,
  GRAVITINO_JOB_FILTER,
  PANEL_QUERIES,
} from '../panelQueries';

export interface GravitinoInstantValues {
  nodeCount: number;
  jettyThreadUsage: number;
  queuedRequests: number;
  activeConnections: number;
  heapUsage: number;
  httpQps: number;
}

export interface GravitinoDashboardData {
  instant: GravitinoInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

export interface UseGravitinoDashboardParams {
  instance: string;
  timeRange: string;
  clusterId: number;
  refreshKey: number;
}

const QUERY_PANEL_IDS = ALL_PANEL_IDS.filter((id) => id !== 'G02');

/** 取最新时间点的全部 series 之和，供概览卡片复用 HTTP QPS 时序查询。 */
export function latestSeriesValue(points: TimeSeriesPoint[]): number {
  const latestTime = Math.max(...points.map((point) => point.time));
  if (!Number.isFinite(latestTime)) return Number.NaN;
  return points
    .filter((point) => point.time === latestTime)
    .reduce((sum, point) => sum + point.value, 0);
}

export function useGravitinoDashboard({
  instance,
  timeRange,
  clusterId,
  refreshKey,
}: UseGravitinoDashboardParams): GravitinoDashboardData {
  const [instances, setInstances] = useState<string[]>([]);

  useEffect(() => {
    if (clusterId <= 0) {
      setInstances([]);
      return;
    }
    fetchDorisLabels('jvm_heap_used', clusterId, GRAVITINO_JOB_FILTER)
      .then((res) => {
        if (res?.data) setInstances(res.data.instances);
      })
      .catch(() => {
        setInstances([]);
      });
  }, [clusterId, refreshKey]);

  const data = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds: QUERY_PANEL_IDS,
    instance,
    job: GRAVITINO_JOB_FILTER,
    timeRange,
    clusterId,
    refreshKey,
  });

  return {
    instant: {
      nodeCount: data.instant.G01 ?? Number.NaN,
      jettyThreadUsage: data.instant.G03 ?? Number.NaN,
      queuedRequests: data.instant.G04 ?? Number.NaN,
      activeConnections: data.instant.G05 ?? Number.NaN,
      heapUsage: data.instant.G06 ?? Number.NaN,
      httpQps: latestSeriesValue(data.series.G07 ?? []),
    },
    series: data.series,
    instances,
    loading: data.loading,
    error: data.error,
  };
}
