export function formatBytes(bytes: number, decimals = 1): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(Math.abs(bytes)) / Math.log(k));
  return `${parseFloat((bytes / k ** i).toFixed(decimals))} ${sizes[i]}`;
}

export interface Threshold {
  value: number | null;
  color: string;
  label?: string;
}

export function colorByThreshold(
  value: number,
  thresholds: Threshold[],
): string {
  const sorted = [...thresholds]
    .filter((t) => t.value !== null)
    .sort((a, b) => (b.value as number) - (a.value as number));

  for (const t of sorted) {
    if (value >= (t.value as number)) return t.color;
  }
  return thresholds.find((t) => t.value === null)?.color ?? '#000000';
}

export function labelByThreshold(
  value: number,
  thresholds: Threshold[],
): string | undefined {
  const sorted = [...thresholds]
    .filter((t) => t.value !== null && t.label !== undefined)
    .sort((a, b) => (b.value as number) - (a.value as number));

  for (const t of sorted) {
    if (value >= (t.value as number)) return t.label;
  }
  return thresholds.find((t) => t.value === null)?.label;
}
