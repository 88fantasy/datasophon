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
import { PANEL_QUERIES } from '../panelQueries';

export interface ApisixInstantValues {
  totalRequests: number;
  acceptedConnections: number;
  handledConnections: number;
  activeConnections: number;
  nginxMetricErrors: number;
}

export interface ApisixDashboardData {
  instant: ApisixInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  jobs: string[];
  loading: boolean;
  error?: string;
}

const ALL_PANEL_IDS = [
  'A01', 'A02', 'A03', 'A04', 'A05',
  'A06', 'A07', 'A08', 'A09', 'A10', 'A11', 'A12',
];

export interface UseApisixDashboardParams {
  variables: { instance: string; job: string };
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useApisixDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseApisixDashboardParams): ApisixDashboardData {
  const [labels, setLabels] = useState<{ instances: string[]; jobs: string[] }>(
    { instances: [], jobs: [] },
  );

  // 用 apisix_http_requests_total 作为标签枚举的基准指标(等价于 Prometheus up 查询)
  useEffect(() => {
    fetchDorisLabels('apisix_http_requests_total', clusterId)
      .then((res) => {
        if (res?.data) setLabels(res.data);
      })
      .catch(() => {
        // labels 查询失败不影响面板数据,静默降级
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
      totalRequests: data.instant.A01 ?? 0,
      acceptedConnections: data.instant.A02 ?? 0,
      handledConnections: data.instant.A03 ?? 0,
      activeConnections: data.instant.A04 ?? 0,
      nginxMetricErrors: data.instant.A05 ?? 0,
    },
    series: data.series,
    instances: labels.instances,
    jobs: labels.jobs,
    loading: data.loading,
    error: data.error,
  };
}
