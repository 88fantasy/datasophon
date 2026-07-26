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

import type { DorisPanelDescriptor } from '../../monitor/_shared/dorisService';

/**
 * 主机指标固定 job='node'：otelcol.ftl 的 `resource/host_metrics` processor
 * 把所有 host_metrics receiver 采集到的指标打上 `service.name=node`。
 */
export const CLUSTER_DASHBOARD_JOB = '^node$';

export const ALL_PANEL_IDS = ['CO-CPU', 'CO-NET', 'CO-MEM-PCT', 'CO-DISK-PCT'];

/**
 * 集群概览看板的 OTel 主机指标查询描述符。
 *
 * metric 名与 filters 直接复用后端已验证过的 `master/service/HostCheckService.java`
 * 常量（FILESYSTEM_FILTERS / FILESYSTEM_FILTERS_NE / DISK_USED_FILTERS /
 * MEMORY_USED_FILTERS），口径已在五节点沙箱用等价 SQL 实测核对（CPU 合计核数与
 * 沙箱 16 vCPU 精确吻合，内存/磁盘总量与硬件规格吻合）。
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  /** CPU 使用率（各节点一条曲线），比值合成：非 idle rate / 总 rate * 100 */
  'CO-CPU': {
    type: 'multi-range',
    queries: [
      {
        label: 'CPU 使用率',
        metric: 'system.cpu.time',
        table: 'sum',
        rate: '1m',
        filtersNe: { state: 'idle' },
        denominatorMetric: 'system.cpu.time',
        denominatorTable: 'sum',
        scale: 100,
      },
    ],
  },
  /** 网络吞吐（各节点按收/发区分），bytes/s */
  'CO-NET': {
    type: 'multi-range',
    queries: [
      {
        label: '网络吞吐',
        metric: 'system.network.io',
        table: 'sum',
        rate: '1m',
        groupBy: ['direction'],
      },
    ],
  },
  /** 内存使用率（集群整体聚合：SUM(已用)/SUM(总量)*100） */
  'CO-MEM-PCT': {
    type: 'instant',
    metric: 'system.memory.usage',
    table: 'sum',
    agg: 'sum',
    filters: { state: 'used' },
    denominatorMetric: 'system.memory.usage',
    denominatorTable: 'sum',
    scale: 100,
  },
  /** 磁盘使用率（集群整体聚合，仅 ext4/xfs 且排除 pod 挂载点） */
  'CO-DISK-PCT': {
    type: 'instant',
    metric: 'system.filesystem.usage',
    table: 'sum',
    agg: 'sum',
    filters: { type: 'ext.*|xfs', state: 'used' },
    filtersNe: { mountpoint: '.*pod.*' },
    denominatorMetric: 'system.filesystem.usage',
    denominatorTable: 'sum',
    denominatorFilters: { type: 'ext.*|xfs' },
    denominatorFiltersNe: { mountpoint: '.*pod.*' },
  },
};
