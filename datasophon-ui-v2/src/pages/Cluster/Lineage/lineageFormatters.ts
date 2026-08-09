import dayjs from 'dayjs';
import type { GraphJob, JobMetrics } from './service';

function formatUnit(value: number): string {
  return String(Number(value.toFixed(1)));
}

export function formatRowCount(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '-';
  const absolute = Math.abs(value);
  if (absolute >= 100_000_000) {
    return `${formatUnit(value / 100_000_000)}亿行`;
  }
  if (absolute >= 10_000) {
    return `${formatUnit(value / 10_000)}万行`;
  }
  return `${new Intl.NumberFormat('zh-CN').format(value)}行`;
}

export function formatBytes(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '-';
  if (value === 0) return '0 B';

  const base = 1024;
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(
    Math.floor(Math.log(Math.abs(value)) / Math.log(base)),
    units.length - 1,
  );
  return `${formatUnit(value / base ** index)} ${units[index]}`;
}

export function formatJobNodeLabel(job: GraphJob, runtimeLabel = '- task · -'): string {
  return `${job.jobName}\n${runtimeLabel}`;
}

export function formatRunningJobLabel(metrics: JobMetrics): string {
  return `${metrics.completeTasks + metrics.activeTasks} task · ${formatRecordsRate(
    metrics.recordsWrittenRate,
  )}`;
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
