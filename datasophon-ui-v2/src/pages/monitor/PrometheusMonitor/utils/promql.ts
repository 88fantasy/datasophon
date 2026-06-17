export interface PrometheusVector {
  resultType: 'vector';
  result: Array<{
    metric: Record<string, string>;
    value: [number, string];
  }>;
}

export interface PrometheusMatrix {
  resultType: 'matrix';
  result: Array<{
    metric: Record<string, string>;
    values: Array<[number, string]>;
  }>;
}

export interface DashboardVariables {
  instance: string;
  job: string;
  interval: string;
}

export interface TableRow {
  instance: string;
  job: string;
  value: number;
  key: string;
}

export function replaceVars(
  promql: string,
  variables: Partial<DashboardVariables>,
): string {
  return promql
    .replace(/\$instance/g, variables.instance || '.+')
    .replace(/\$job/g, variables.job || '.+')
    .replace(/\$interval/g, variables.interval || '5m');
}

export function vectorToTableRows(vector: PrometheusVector): TableRow[] {
  return vector.result.map((item) => {
    const instance = item.metric.instance ?? '';
    const job = item.metric.job ?? '';

    return {
      instance,
      job,
      value: Number.parseFloat(item.value[1]),
      key: `${instance}-${job}`,
    };
  });
}

export function selectionsToRegex(values: string[]): string {
  if (values.length === 0) return '.+';
  return values
    .map((value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|');
}

// ─── 数据转换工具 ──────────────────────────────────────────────────────────────
// 以下函数供 usePrometheusDashboard hook 将后端返回的 Prometheus 原始数据
// 转换为面板组件所需的 TimeSeriesPoint[] / number / TableRow[] 等格式。

import type { TimeSeriesPoint } from '../../_shared/types';

/** Prometheus 保留 label 集合，确定 series 名时跳过这些 key。 */
const RESERVED_LABELS = new Set(['__name__', 'job', 'instance']);

/**
 * 从 matrix 结果提取时序数据点列表。
 *
 * @param matrix  - Prometheus range query 返回值
 * @param seriesKey - 用作 series 名称的 label key（如 'instance'/'scrape_job'）。
 *   缺省时取第一个非保留 label；仍无则回退到 '__name__' 或 'series'。
 */
export function matrixToSeries(
  matrix: PrometheusMatrix,
  seriesKey?: string,
): TimeSeriesPoint[] {
  const points: TimeSeriesPoint[] = [];
  for (const item of matrix.result) {
    const seriesName = resolveSeriesName(item.metric, seriesKey);
    for (const [ts, val] of item.values) {
      points.push({
        time: ts * 1000,
        value: Number.parseFloat(val),
        series: seriesName,
      });
    }
  }
  return points;
}

/**
 * 多 PromQL 面板：把若干已带固定 label 的 matrix 合并为多系列数据。
 *
 * 用于 P12/P14/P19/P22 等"多条 PromQL → 多条 series"面板：
 * 每个 part 的所有 result 都被赋予同一 label，然后拼入结果集。
 */
export function mergeNamedSeries(
  parts: Array<{ label: string; matrix: PrometheusMatrix }>,
): TimeSeriesPoint[] {
  const points: TimeSeriesPoint[] = [];
  for (const { label, matrix } of parts) {
    for (const item of matrix.result) {
      for (const [ts, val] of item.values) {
        points.push({
          time: ts * 1000,
          value: Number.parseFloat(val),
          series: label,
        });
      }
    }
  }
  return points;
}

/**
 * 从 instant query vector 提取单个数值（供 P01–P06 stat 面板）。
 *
 * 若 vector 为空，返回 0（防止页面崩溃）。
 */
export function vectorToScalar(vector: PrometheusVector): number {
  if (vector.result.length === 0) return 0;
  return Number.parseFloat(vector.result[0].value[1]);
}

/**
 * 从 `up` 的 instant vector 派生工具栏实例/Job 下拉选项。
 *
 * 用于替换 MOCK_INSTANCES / MOCK_JOBS，展示真实 Prometheus scrape target。
 */
export function deriveInstancesAndJobs(vector: PrometheusVector): {
  instances: string[];
  jobs: string[];
} {
  const instances = new Set<string>();
  const jobs = new Set<string>();
  for (const item of vector.result) {
    if (item.metric.instance) instances.add(item.metric.instance);
    if (item.metric.job) jobs.add(item.metric.job);
  }
  return { instances: [...instances], jobs: [...jobs] };
}

// ─── 内部工具 ─────────────────────────────────────────────────────────────────

function resolveSeriesName(
  metric: Record<string, string>,
  seriesKey?: string,
): string {
  if (seriesKey && metric[seriesKey]) return metric[seriesKey];
  // 取第一个非保留 label
  for (const [key, val] of Object.entries(metric)) {
    if (!RESERVED_LABELS.has(key)) return val;
  }
  // 回退
  return metric.__name__ ?? metric.instance ?? 'series';
}
