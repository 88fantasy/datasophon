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
import {
  getClusterDashboardSummary,
  getRecentAlerts,
} from '@/services/clusterDashboard';

const RECENT_ALERTS_PAGE_SIZE = 5;

export interface ClusterSummaryData {
  summary?: DATASOPHON.ClusterDashboardResponse;
  recentAlerts: DATASOPHON.ClusterAlertHistoryRecord[];
  loading: boolean;
  error?: string;
}

export interface UseClusterSummaryParams {
  clusterId: number;
  refreshKey: number;
}

/** 集群概览看板的 DB 聚合数据 hook：统计数字/告警趋势/服务健康度/集群概要 + 最新告警。 */
export function useClusterSummary({
  clusterId,
  refreshKey,
}: UseClusterSummaryParams): ClusterSummaryData {
  const [data, setData] = useState<ClusterSummaryData>({
    recentAlerts: [],
    loading: true,
  });

  useEffect(() => {
    let cancelled = false;

    if (clusterId <= 0) {
      setData({ recentAlerts: [], loading: false });
      return;
    }

    setData((prev) => ({ ...prev, loading: true }));

    Promise.allSettled([
      getClusterDashboardSummary(clusterId),
      getRecentAlerts(clusterId, RECENT_ALERTS_PAGE_SIZE),
    ]).then(([summaryResult, alertsResult]) => {
      if (cancelled) return;

      const errors: string[] = [];
      if (summaryResult.status === 'rejected') {
        errors.push(
          summaryResult.reason instanceof Error
            ? summaryResult.reason.message
            : 'Unknown error',
        );
      }
      if (alertsResult.status === 'rejected') {
        errors.push(
          alertsResult.reason instanceof Error
            ? alertsResult.reason.message
            : 'Unknown error',
        );
      }

      setData((prev) => ({
        summary:
          summaryResult.status === 'fulfilled'
            ? summaryResult.value.data
            : prev.summary,
        recentAlerts:
          alertsResult.status === 'fulfilled'
            ? (alertsResult.value.data ?? [])
            : prev.recentAlerts,
        loading: false,
        error: errors.length > 0 ? errors.join('; ') : undefined,
      }));
    });

    return () => {
      cancelled = true;
    };
  }, [clusterId, refreshKey]);

  return data;
}
