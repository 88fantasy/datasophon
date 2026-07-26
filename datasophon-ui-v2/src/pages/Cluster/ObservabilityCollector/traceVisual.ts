/**
 * dorisexporter 的 otel_traces.duration 使用微秒；接口沿用该原始值。
 */
export function formatDuration(durationUs: number) {
  if (!durationUs) return '-';
  const ms = durationUs / 1_000;
  if (ms < 1) return `${durationUs.toFixed(1)} us`;
  if (ms < 1000) return `${ms.toFixed(ms < 10 ? 2 : 1)} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

export function durationBarWidth(duration: number, maxDuration: number) {
  if (duration <= 0 || maxDuration <= 0) return 8;
  const ratio = Math.log1p(duration) / Math.log1p(maxDuration);
  return Math.round(8 + ratio * 112);
}
