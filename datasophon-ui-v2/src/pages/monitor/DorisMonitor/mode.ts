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

import type { DorisPanelDescriptor } from '../_shared/dorisService';
import { DORIS_SEGMENT_PANEL_IDS, PANEL_QUERIES } from './panelQueries';
import {
  DISAGG_PANEL_QUERIES,
  DISAGG_PANEL_ROLE,
  DISAGG_SEGMENT_PANEL_IDS,
} from './panelQueriesDisaggregated';

export type DorisMonitorMode = 'coupled' | 'disaggregated';

/** 后端登记时写入 t_ddh_k8s_service_instance.monitor_profile 的 JSON 结构 */
export interface DorisMonitorProfile {
  profile?: string;
  /** 角色名（fe/compute）→ 该角色的 job（Doris service_name）列表 */
  roles?: Record<string, string[]>;
}

/** 解析 monitorProfile JSON 原文；空值或解析失败时返回 undefined，不抛错。 */
export function parseDorisMonitorProfile(
  raw?: string,
): DorisMonitorProfile | undefined {
  if (!raw) return undefined;
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/**
 * 模式判定信号 = 后端下发的 monitorProfile.profile（显式），不从 job 名猜测（D-5）。
 * 未接管为 CR 或 profile 字段缺失时一律按耦合模式（存算一体）处理，与既有 MANAGED
 * 集群行为保持一致。
 */
export function resolveDorisMode(monitorProfile?: string): DorisMonitorMode {
  const parsed = parseDorisMonitorProfile(monitorProfile);
  return parsed?.profile === 'doris-disaggregated'
    ? 'disaggregated'
    : 'coupled';
}

export interface DorisPanelSet {
  queries: Record<string, DorisPanelDescriptor>;
  segmentPanelIds: Record<string, string[]>;
}

/** 按模式选取面板描述符 map + 段→面板 id 分组，两套互不影响（D-4）。 */
export function getDorisPanelSet(mode: DorisMonitorMode): DorisPanelSet {
  return mode === 'disaggregated'
    ? {
        queries: DISAGG_PANEL_QUERIES,
        segmentPanelIds: DISAGG_SEGMENT_PANEL_IDS,
      }
    : { queries: PANEL_QUERIES, segmentPanelIds: DORIS_SEGMENT_PANEL_IDS };
}

/**
 * 按 DISAGG_PANEL_ROLE 把一批面板 id 拆成 fe/compute 两组，供存算分离模式下
 * useDorisMonitorDashboard 分流成两次 useDorisDashboardData 调用（各自传各自角色
 * 的 job 过滤）。仅供 disaggregated 模式调用；耦合模式没有角色拆分概念，调用方
 * 应直接跳过本函数、把全部 panelIds 传给第一次调用、第二次调用传空数组。
 */
export function splitPanelIdsByRole(panelIds: string[]): {
  feIds: string[];
  computeIds: string[];
} {
  const feIds: string[] = [];
  const computeIds: string[] = [];
  for (const id of panelIds) {
    if (DISAGG_PANEL_ROLE[id] === 'compute') {
      computeIds.push(id);
    } else {
      feIds.push(id);
    }
  }
  return { feIds, computeIds };
}
