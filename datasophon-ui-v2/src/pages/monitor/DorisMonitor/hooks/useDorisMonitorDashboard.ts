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
import { metricsJobToRegex, selectionsToRegex } from '../../_shared/charts/promql';
import { fetchDorisLabels } from '../../_shared/dorisService';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  type DorisMonitorMode,
  getDorisPanelSet,
  parseDorisMonitorProfile,
  resolveDorisMode,
  splitPanelIdsByRole,
} from '../mode';
import type { DorisDashboardSegment } from '../panelQueries';
import { DORIS_SEGMENT_PANEL_IDS } from '../panelQueries';
import { DISAGG_SEGMENT_PANEL_IDS } from '../panelQueriesDisaggregated';

export interface DorisInstantValues {
  feNodeCount: number;
  feAliveCount: number;
  beNodeCount: number;
  beAliveCount: number;
  usedCapacityBytes: number;
  totalCapacityBytes: number;
}

export interface DorisDashboardData {
  mode: DorisMonitorMode;
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
  /**
   * 接管实例登记的 metricsJob（逗号分隔）。仅耦合模式下生效，作为整个看板的
   * job 过滤（与 ZooKeeperMonitor 等既有 K8s 看板同一范式）；存算分离模式下
   * 角色 job 改由 monitorProfile.roles 提供，本字段被忽略。
   */
  job?: string;
  /**
   * 接管登记的 monitor_profile JSON 原文（CR 来源专用）。存在且
   * profile===doris-disaggregated 时判定为存算分离模式；未接管的 MANAGED
   * 集群不传，走既有耦合模式行为，零回归（D-4/D-5）。
   */
  monitorProfile?: string;
}

const COUPLED_ALL_IDS = [
  ...DORIS_SEGMENT_PANEL_IDS.cluster,
  ...DORIS_SEGMENT_PANEL_IDS.fe,
  ...DORIS_SEGMENT_PANEL_IDS.be,
];
const DISAGG_ALL_IDS = [
  ...DISAGG_SEGMENT_PANEL_IDS.cluster,
  ...DISAGG_SEGMENT_PANEL_IDS.fe,
  ...DISAGG_SEGMENT_PANEL_IDS.compute,
];

const EMPTY_SERIES_COUPLED: Record<string, TimeSeriesPoint[]> =
  Object.fromEntries(COUPLED_ALL_IDS.map((id) => [id, []]));
const EMPTY_SERIES_DISAGG: Record<string, TimeSeriesPoint[]> =
  Object.fromEntries(DISAGG_ALL_IDS.map((id) => [id, []]));

export function useDorisMonitorDashboard({
  variables,
  activeSegment,
  timeRange,
  clusterId = 1,
  refreshKey,
  job,
  monitorProfile,
}: UseDorisMonitorDashboardParams): DorisDashboardData {
  const [feInstances, setFeInstances] = useState<string[]>([]);
  const [beInstances, setBeInstances] = useState<string[]>([]);
  const [clusters, setClusters] = useState<string[]>([]);

  const mode = useMemo(() => resolveDorisMode(monitorProfile), [monitorProfile]);
  const profile = useMemo(
    () => parseDorisMonitorProfile(monitorProfile),
    [monitorProfile],
  );
  // 存算分离下 FE/计算组各自的 job 正则；耦合模式复用既有 job prop 范式（整个看板一个过滤）。
  const coupledJob = mode === 'coupled' ? metricsJobToRegex(job) : undefined;
  const feJob =
    mode === 'disaggregated'
      ? selectionsToRegex(profile?.roles?.fe ?? [])
      : coupledJob;
  const computeJob =
    mode === 'disaggregated'
      ? selectionsToRegex(profile?.roles?.compute ?? [])
      : coupledJob;

  // 用 doris_fe_query_total / doris_be_memory_allocated_bytes 作为标签枚举基准；
  // 存算分离下按角色 job 过滤，只统计最近 5 分钟有上报的实例（后端 queryLabels 语义）。
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchDorisLabels('doris_fe_query_total', clusterId, feJob),
      fetchDorisLabels('doris_be_memory_allocated_bytes', clusterId, computeJob),
    ])
      .then(([feRes, beRes]) => {
        if (cancelled) return;
        setFeInstances(feRes?.data?.instances ?? []);
        setBeInstances(beRes?.data?.instances ?? []);
        setClusters(feRes?.data?.jobs ?? []);
      })
      .catch(() => {
        // labels 查询失败不影响面板数据，静默降级
      });
    return () => {
      cancelled = true;
    };
  }, [clusterId, refreshKey, feJob, computeJob]);

  const panelSet = useMemo(() => getDorisPanelSet(mode), [mode]);

  /**
   * ⚠️ 多 segment 硬约束：只传当前 segment 的 panelIds，避免一次性拉全部面板超时。
   * activeSegment 变化 → panelIds 变化 → useDorisDashboardData 重拉。
   */
  const panelIds = useMemo(
    () => panelSet.segmentPanelIds[activeSegment] ?? [],
    [panelSet, activeSegment],
  );

  // 存算分离下按角色拆分成两组 id（cluster 段混合了 FE/计算组指标，不能整段共用一个 job）；
  // 耦合模式没有角色拆分概念，全部 id 走第一次调用，第二次调用传空数组（不得跳过 hook 调用本身）。
  const { feIds: fePanelIds, computeIds: computePanelIds } = useMemo(() => {
    if (mode !== 'disaggregated') {
      return { feIds: panelIds, computeIds: [] as string[] };
    }
    return splitPanelIdsByRole(panelIds);
  }, [mode, panelIds]);

  // 不同 segment 传入不同 instance 过滤（存算分离的 compute 段复用 be 的实例选择槽位）
  const instance =
    activeSegment === 'fe'
      ? variables.feInstance || '.+'
      : activeSegment === 'be' || activeSegment === 'compute'
        ? variables.beInstance || '.+'
        : '.+';

  const feData = useDorisDashboardData({
    panelDescriptors: panelSet.queries,
    panelIds: fePanelIds,
    instance,
    job: feJob,
    timeRange,
    clusterId,
    refreshKey,
  });

  const computeData = useDorisDashboardData({
    panelDescriptors: panelSet.queries,
    panelIds: computePanelIds,
    instance,
    job: computeJob,
    timeRange,
    clusterId,
    refreshKey,
  });

  const mergedInstant = { ...feData.instant, ...computeData.instant };
  const emptySeries =
    mode === 'disaggregated' ? EMPTY_SERIES_DISAGG : EMPTY_SERIES_COUPLED;

  return {
    mode,
    instant:
      mode === 'disaggregated'
        ? {
            // DO-A01–A06（角色注册表节点数 / 本地磁盘容量）在存算分离下没有对应数据源，
            // 节点数改用 labels 接口的"最近 5 分钟有上报"实例计数；本地磁盘容量类字段
            // 不适用（无本地磁盘），置 0，index.tsx 按 mode 跳过对应 StatPanel 渲染。
            feNodeCount: feInstances.length,
            feAliveCount: feInstances.length,
            beNodeCount: beInstances.length,
            beAliveCount: beInstances.length,
            usedCapacityBytes: 0,
            totalCapacityBytes: 0,
          }
        : {
            feNodeCount: mergedInstant['DO-A01'] ?? 0,
            feAliveCount: mergedInstant['DO-A02'] ?? 0,
            beNodeCount: mergedInstant['DO-A03'] ?? 0,
            beAliveCount: mergedInstant['DO-A04'] ?? 0,
            usedCapacityBytes: mergedInstant['DO-A05'] ?? 0,
            totalCapacityBytes: mergedInstant['DO-A06'] ?? 0,
          },
    series: { ...emptySeries, ...feData.series, ...computeData.series },
    clusters: clusters.length > 0 ? clusters : ['doris'],
    feInstances,
    beInstances,
    loading: feData.loading || computeData.loading,
    error: feData.error ?? computeData.error,
  };
}
