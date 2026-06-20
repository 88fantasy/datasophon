import { describe, expect, it } from 'vitest';
import { formatBytes } from './formatters';

describe('formatBytes', () => {
  it('formats zero and normal byte magnitudes', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(1024)).toBe('1 KB');
    expect(formatBytes(1024 * 1024)).toBe('1 MB');
  });

  it('returns "-" for non-finite input instead of "NaN undefined"', () => {
    // NaN 常见于缺失 series 或 Prometheus 对 0/0 返回字符串 "NaN"。
    expect(formatBytes(Number.NaN)).toBe('-');
    expect(formatBytes(Number.POSITIVE_INFINITY)).toBe('-');
  });
});
