export const CHART_COLORS = {
  primary: '#1677ff',
  success: '#52c41a',
  warning: '#faad14',
  error: '#ff4d4f',
  series: [
    '#1677ff',
    '#52c41a',
    '#faad14',
    '#ff4d4f',
    '#722ed1',
    '#eb2f96',
    '#13c2c2',
    '#fa8c16',
  ],
};

export function colorByThreshold(
  value: number,
  thresholds: [number, number],
  opts?: { reverse?: boolean },
): string {
  const [warnThreshold, criticalThreshold] = thresholds;

  if (opts?.reverse) {
    if (value >= criticalThreshold) return CHART_COLORS.success;
    if (value >= warnThreshold) return CHART_COLORS.warning;
    return CHART_COLORS.error;
  }

  if (value < warnThreshold) return CHART_COLORS.success;
  if (value < criticalThreshold) return CHART_COLORS.warning;
  return CHART_COLORS.error;
}

export function formatBytes(bytes: number, decimals = 0): string {
  // 非有限值（NaN/Infinity，常见于 Prometheus 对 0/0 返回字符串 "NaN" 或缺失 series）→ '–'，
  // 否则会渲染出 'NaN undefined'（Math.log(NaN)→NaN → sizes[NaN]→undefined）。
  if (!Number.isFinite(bytes)) return '-';
  if (bytes === 0) return '0 B';

  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(
    Math.floor(Math.log(Math.abs(bytes)) / Math.log(k)),
    sizes.length - 1,
  );
  const value = bytes / k ** index;

  return `${parseFloat(value.toFixed(decimals))} ${sizes[index]}`;
}

export function formatCompact(value: number): string {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 0,
  }).format(value);
}
