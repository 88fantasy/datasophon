import { describe, expect, it } from 'vitest';
import {
  currentlyDownRows,
  instantValues,
  prometheusSeriesData,
} from './prometheusMockData';

describe('PrometheusMonitor mock data', () => {
  it('contains the expected instant panel values', () => {
    expect(instantValues).toMatchObject({
      uptime: 99.8,
      totalSeries: 125_000,
      memoryChunks: 45_600,
      reloadFailures: 0,
      missedIterations: 0,
      skippedScrapes: 2,
    });
    expect(currentlyDownRows).toEqual([]);
  });

  it('covers every range panel from P08 through P24', () => {
    const expectedIds = Array.from({ length: 17 }, (_, index) =>
      `P${String(index + 8).padStart(2, '0')}`,
    );

    expect(Object.keys(prometheusSeriesData).sort()).toEqual(expectedIds);
    for (const id of expectedIds) {
      expect(prometheusSeriesData[id]).not.toHaveLength(0);
    }
  });
});
