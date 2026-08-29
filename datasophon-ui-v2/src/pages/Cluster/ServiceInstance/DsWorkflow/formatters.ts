import dayjs from 'dayjs';
import { formatBytes } from '@/pages/Cluster/Lineage/lineageFormatters';

// DS Open API 返回的 updateTime/startTime 本身就是服务器本地时间（无时区后缀），
// 不是 UTC——之前用 dayjs.utc(value).local() 把它当 UTC 解析再转本地，凭空多算了
// 一个时区偏移（实测 +8 小时）。这里直接按本地时间解析，不做时区假设。
export function formatDsTime(value?: string): string {
  if (!value) return '—';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : '—';
}

export function formatDuration(seconds?: number): string {
  if (seconds == null || !Number.isFinite(seconds)) return '—';
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return remainder === 0 ? `${minutes}m` : `${minutes}m ${remainder}s`;
}

export function formatRows(value?: number): string {
  return value == null || !Number.isFinite(value)
    ? '—'
    : new Intl.NumberFormat('en-US').format(value);
}

export function formatOutputSize(value?: number): string {
  const formatted = formatBytes(value);
  return formatted === '-' ? '—' : formatted;
}

export function formatRate(value?: number): string {
  return value == null || !Number.isFinite(value)
    ? '—'
    : `${value.toFixed(1)} row/s`;
}

export function shortDatasetName(value: string): string {
  const pieces = value.split(/[/.]/).filter(Boolean);
  return pieces.at(-1) ?? value;
}
