import type { PrometheusTableRow, TimeSeriesPoint } from '../types';

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
  [key: string]: string;
}

export interface TableRow {
  instance: string;
  job: string;
  value: number;
  key: string;
}

/**
 * 通用变量替换：把 promql 中的 `$key` 替换为 vars[key]。
 *
 * 支持任意 key，defaults 提供缺省值（如 { instance: '.+', job: '.+' }）。
 * 各看板专属的 replaceXVars 可直接调用此函数并填入对应 defaults。
 */
export function replaceVars(
  promql: string,
  vars: Record<string, string>,
  defaults?: Record<string, string>,
): string {
  const merged = { ...defaults, ...vars };
  return Object.entries(merged).reduce(
    (result, [key, value]) =>
      result.replace(
        new RegExp(`\\$${key}`, 'g'),
        value || defaults?.[key] || '',
      ),
    promql,
  );
}

export function vectorToTableRows(
  vector: PrometheusVector,
): PrometheusTableRow[] {
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
 * 从 instant query vector 提取单个数值（供 stat 面板）。
 *
 * 若 vector 为空，返回 0（防止页面崩溃）。
 */
export function vectorToScalar(vector: PrometheusVector): number {
  if (vector.result.length === 0) return 0;
  return Number.parseFloat(vector.result[0].value[1]);
}

/**
 * 从 `up` 的 instant vector 派生工具栏实例/Job 下拉选项。
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

function resolveSeriesName(
  metric: Record<string, string>,
  seriesKey?: string,
): string {
  if (seriesKey && metric[seriesKey]) return metric[seriesKey];
  for (const [key, val] of Object.entries(metric)) {
    if (!RESERVED_LABELS.has(key)) return val;
  }
  return metric.__name__ ?? metric.instance ?? 'series';
}
