import { describe, expect, it } from 'vitest';
import { CHART_COLORS, colorByThreshold, formatBytes } from './formatters';

describe('PrometheusMonitor formatters', () => {
  it('colors high uptime as healthy with reverse thresholds', () => {
    expect(colorByThreshold(99.8, [90, 99], { reverse: true })).toBe(
      CHART_COLORS.success,
    );
    expect(colorByThreshold(94.2, [90, 99], { reverse: true })).toBe(
      CHART_COLORS.warning,
    );
    expect(colorByThreshold(80, [90, 99], { reverse: true })).toBe(
      CHART_COLORS.error,
    );
  });

  it('colors low error counts as healthy with default thresholds', () => {
    expect(colorByThreshold(0, [1, 10])).toBe(CHART_COLORS.success);
    expect(colorByThreshold(3, [1, 10])).toBe(CHART_COLORS.warning);
    expect(colorByThreshold(12, [1, 10])).toBe(CHART_COLORS.error);
  });

  it('formats bytes using binary units', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(1024)).toBe('1 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5 MB');
  });
});
