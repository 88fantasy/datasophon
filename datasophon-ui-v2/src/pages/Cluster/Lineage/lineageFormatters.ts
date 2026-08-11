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

/**
 * 作业节点标签：第一行永远是任务名，第二行是运行态信息。
 *
 * 有实时指标（作业正在跑）时用 `runtimeLabel`；没有则回退到历史统计"行数 · 相对时间"，
 * 而不是显示占位的 `- task · -`——历史作业的这两个字段本来就查得到，退成占位是白白丢信息。
 */
export function formatJobNodeLabel(
  job: GraphJob,
  runtimeLabel?: string,
  now = Date.now(),
): string {
  if (runtimeLabel) {
    return `${job.jobName}\n${runtimeLabel}`;
  }
  if (job.lastRowCount === null || job.lastRunAt === null) {
    return job.jobName;
  }
  const relativeTime = formatRelativeTime(job.lastRunAt, now);
  if (!relativeTime) return job.jobName;
  return `${job.jobName}\n${formatRowCount(job.lastRowCount)} · ${relativeTime}`;
}

/**
 * Flink 没有 Spark 那样的批式 task 生命周期：它的 `completeTasks` 恒为 0，
 * `activeTasks` 是并行 subtask 数；Spark 的 `completeTasks` 则是累计完成数。
 * 两者相加会得到一个在两种引擎下含义不同的数字，所以按引擎分别取。
 */
export function formatRunningJobLabel(metrics: JobMetrics): string {
  const tasks =
    metrics.engine === 'FLINK'
      ? `${metrics.activeTasks} task`
      : `✓${metrics.completeTasks} task`;
  return `${tasks} · ${formatRecordsRate(metrics.recordsWrittenRate)}`;
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
