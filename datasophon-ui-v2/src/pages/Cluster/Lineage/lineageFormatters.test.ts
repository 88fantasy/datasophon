import { describe, expect, it } from 'vitest';
import {
  formatBytes,
  formatJobNodeLabel,
  formatRecordsRate,
  formatRowCount,
  formatRunningJobLabel,
} from './lineageFormatters';
import type { JobMetrics } from './service';
import type { GraphJob } from './service';

const JOB: GraphJob = {
  jobId: 10,
  edgeId: 100,
  flowType: 'OUTPUT',
  jobName: 'daily_orders_etl',
  lastRowCount: 1_200_000,
  lastBytes: 64 * 1024 * 1024,
  lastRunAt: '2026-08-04T03:00:00Z',
  runningAppId: null,
};

const METRICS: JobMetrics = {
  completeTasks: 12,
  activeTasks: 2,
  recordsWritten: 60_000_000,
  bytesWritten: 2_204_955_464,
  recordsWrittenRate: 51_234.5,
  runningStages: 1,
  sampledAt: '2026-08-04T03:01:44Z',
  engine: 'SPARK',
};

describe('lineage formatters', () => {
  it('formats row counts using Chinese ten-thousand and hundred-million units', () => {
    expect(formatRowCount(1_200_000)).toBe('120万行');
    expect(formatRowCount(250_000_000)).toBe('2.5亿行');
  });

  it('formats bytes using binary units', () => {
    expect(formatBytes(64 * 1024 * 1024)).toBe('64 MB');
    expect(formatBytes(2.5 * 1024 * 1024 * 1024)).toBe('2.5 GB');
  });

  it('falls back to historical statistics when the job has no live metrics', () => {
    // 没有运行态指标不等于没有信息可显示：lastRowCount / lastRunAt 本来就查得到，
    // 退成 "- task · -" 这种占位是白白丢掉历史作业唯一能展示的东西。
    expect(
      formatJobNodeLabel(JOB, undefined, new Date('2026-08-04T03:03:00Z').getTime()),
    ).toBe('daily_orders_etl\n120万行 · 3分钟前');
  });

  it('shows only the job name when neither live metrics nor history exist', () => {
    expect(
      formatJobNodeLabel(
        { ...JOB, lastRowCount: null, lastBytes: null, lastRunAt: null },
        undefined,
        new Date('2026-08-04T03:03:00Z').getTime(),
      ),
    ).toBe('daily_orders_etl');
  });

  it('keeps the task name while updating the second line with runtime data', () => {
    expect(formatJobNodeLabel(JOB, '14 task · 5.1万行/秒')).toBe(
      'daily_orders_etl\n14 task · 5.1万行/秒',
    );
  });

  it('labels Spark task counts as completed and Flink ones as parallel subtasks', () => {
    // Spark 的 completeTasks 是累计完成数，Flink 恒为 0 而 activeTasks 才是并行度。
    // 两者相加会得到一个在两种引擎下含义不同的数字，所以按 engine 分别取。
    expect(formatRunningJobLabel(METRICS)).toBe('✓12 task · 5.1万行/秒');
    expect(
      formatRunningJobLabel({
        ...METRICS,
        engine: 'FLINK',
        completeTasks: 0,
        activeTasks: 14,
      }),
    ).toBe('14 task · 5.1万行/秒');
    expect(formatRecordsRate(METRICS.recordsWrittenRate)).toBe('5.1万行/秒');
  });

  it('renders missing row counts as unavailable instead of NaN', () => {
    expect(formatRowCount(undefined)).toBe('-');
    expect(formatRowCount(null)).toBe('-');
    expect(formatBytes(undefined)).toBe('-');
    expect(formatBytes(null)).toBe('-');
  });
});
