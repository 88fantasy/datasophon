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

export interface RustfsInstantValues {
  uptime: number;
  buckets: number;
  objects: number;
  drivesOnline: number;
  drivesOffline: number;
}

export interface RustfsDashboardData {
  instant: RustfsInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  jobs: string[];
  loading: boolean;
  error?: string;
}

const ALL_PANEL_IDS = Array.from(
  { length: 18 },
  (_, i) => `R${String(i + 1).padStart(2, '0')}`,
);

export interface UseRustfsDashboardParams {
  variables: { instance: string; job: string };
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useRustfsDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseRustfsDashboardParams): RustfsDashboardData {
  const [labels, setLabels] = useState<{ instances: string[]; jobs: string[] }>(
    { instances: [], jobs: [] },
  );

  // 用 rustfs_process_uptime_seconds 作为标签枚举的基准指标（恒定上报，等价于 Nexus 用 jvm_vm_uptime）
  useEffect(() => {
    fetchDorisLabels('rustfs_process_uptime_seconds', clusterId)
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
      uptime: data.instant.R01 ?? 0,
      buckets: data.instant.R02 ?? 0,
      objects: data.instant.R03 ?? 0,
      drivesOnline: data.instant.R04 ?? 0,
      drivesOffline: data.instant.R05 ?? 0,
    },
    series: data.series,
    instances: labels.instances,
    jobs: labels.jobs,
    loading: data.loading,
    error: data.error,
  };
}
