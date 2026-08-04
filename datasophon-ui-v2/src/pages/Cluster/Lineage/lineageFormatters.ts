import dayjs from 'dayjs';
import type { GraphJob, JobMetrics } from './service';

function formatUnit(value: number): string {
  return String(Number(value.toFixed(1)));
}

export function formatRowCount(value: number): string {
  if (!Number.isFinite(value)) return '-';
  const absolute = Math.abs(value);
  if (absolute >= 100_000_000) {
    return `${formatUnit(value / 100_000_000)}亿行`;
  }
  if (absolute >= 10_000) {
    return `${formatUnit(value / 10_000)}万行`;
  }
  return `${new Intl.NumberFormat('zh-CN').format(value)}行`;
}

export function formatBytes(value: number): string {
  if (!Number.isFinite(value)) return '-';
  if (value === 0) return '0 B';

  const base = 1024;
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(
    Math.floor(Math.log(Math.abs(value)) / Math.log(base)),
    units.length - 1,
  );
  return `${formatUnit(value / base ** index)} ${units[index]}`;
}

function formatRelativeTime(value: string, now: number): string | null {
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return null;

  const seconds = Math.max(0, Math.floor((now - timestamp) / 1000));
  if (seconds < 60) return '刚刚';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟前`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}小时前`;
  if (seconds < 2_592_000) return `${Math.floor(seconds / 86_400)}天前`;
  if (seconds < 31_536_000) return `${Math.floor(seconds / 2_592_000)}个月前`;
  return `${Math.floor(seconds / 31_536_000)}年前`;
}

export function formatJobNodeLabel(job: GraphJob, now = Date.now()): string {
  if (job.lastRowCount === null || job.lastRunAt === null) {
    return job.jobName;
  }
  const relativeTime = formatRelativeTime(job.lastRunAt, now);
  if (!relativeTime) return job.jobName;
  return `${job.jobName}\n${formatRowCount(job.lastRowCount)} · ${relativeTime}`;
}

export function formatRunningJobLabel(metrics: JobMetrics): string {
  return `✓${metrics.completeTasks} task · ${formatRowCount(metrics.recordsWritten)}`;
}

export function formatRecordsRate(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '-';
  return formatRowCount(value).replace(/行$/, '行/秒');
}

export function formatRunAt(value: string | null): string {
  if (!value) return '-';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : '-';
}
