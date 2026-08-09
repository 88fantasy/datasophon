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

  it('builds a two-line job label with the task name first', () => {
    expect(formatJobNodeLabel(JOB)).toBe('daily_orders_etl\n- task · -');
  });

  it('keeps the task name while updating the second line with runtime data', () => {
    expect(formatJobNodeLabel(JOB, '14 task · 5.1万行/秒')).toBe(
      'daily_orders_etl\n14 task · 5.1万行/秒',
    );
  });

  it('formats running job progress and write rate', () => {
    expect(formatRunningJobLabel(METRICS)).toBe('14 task · 5.1万行/秒');
    expect(formatRecordsRate(METRICS.recordsWrittenRate)).toBe('5.1万行/秒');
  });

  it('renders missing row counts as unavailable instead of NaN', () => {
    expect(formatRowCount(undefined)).toBe('-');
    expect(formatRowCount(null)).toBe('-');
    expect(formatBytes(undefined)).toBe('-');
    expect(formatBytes(null)).toBe('-');
  });
});
