import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { formatBytes } from '@/pages/Cluster/Lineage/lineageFormatters';

dayjs.extend(utc);

export function formatDsTime(value?: string): string {
  if (!value) return '—';
  const parsed = dayjs.utc(value);
  return parsed.isValid() ? parsed.local().format('YYYY-MM-DD HH:mm:ss') : '—';
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
