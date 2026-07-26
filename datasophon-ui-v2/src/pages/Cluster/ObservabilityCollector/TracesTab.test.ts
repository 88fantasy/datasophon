import { describe, expect, it } from 'vitest';

import { durationBarWidth, formatDuration } from './traceVisual';

describe('formatDuration', () => {
  it('formats dorisexporter trace durations expressed in microseconds', () => {
    expect(formatDuration(571)).toBe('571.0 us');
    expect(formatDuration(23_236)).toBe('23.2 ms');
    expect(formatDuration(1_500_000)).toBe('1.50 s');
  });
});

describe('durationBarWidth', () => {
  it('keeps small durations visible and scales relative to the slowest row', () => {
    expect(durationBarWidth(0, 1_000)).toBe(8);
    expect(durationBarWidth(1_000, 1_000)).toBe(120);
    expect(durationBarWidth(10, 1_000)).toBeGreaterThan(8);
    expect(durationBarWidth(10, 1_000)).toBeLessThan(120);
  });
});
