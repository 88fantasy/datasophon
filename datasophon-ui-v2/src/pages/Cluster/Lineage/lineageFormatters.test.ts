import { describe, expect, it } from 'vitest';
import {
  formatBytes,
  formatJobNodeLabel,
  formatRowCount,
} from './lineageFormatters';
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

describe('lineage formatters', () => {
  it('formats row counts using Chinese ten-thousand and hundred-million units', () => {
    expect(formatRowCount(1_200_000)).toBe('120万行');
    expect(formatRowCount(250_000_000)).toBe('2.5亿行');
  });

  it('formats bytes using binary units', () => {
    expect(formatBytes(64 * 1024 * 1024)).toBe('64 MB');
    expect(formatBytes(2.5 * 1024 * 1024 * 1024)).toBe('2.5 GB');
  });

  it('builds a two-line job label with row count and relative time', () => {
    expect(
      formatJobNodeLabel(JOB, new Date('2026-08-04T03:03:00Z').getTime()),
    ).toBe('daily_orders_etl\n120万行 · 3分钟前');
  });

  it('shows only the job name when historical statistics are absent', () => {
    expect(
      formatJobNodeLabel({
        ...JOB,
        lastRowCount: null,
        lastBytes: null,
        lastRunAt: null,
      }),
    ).toBe('daily_orders_etl');
  });
});
