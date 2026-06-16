import { describe, expect, it } from 'vitest';
import { runWithConcurrencyLimit } from './useDorisMonitorDashboard';

describe('Doris monitor concurrency limiter', () => {
  it('runs dashboard queries without exceeding the configured concurrency', async () => {
    let active = 0;
    let maxActive = 0;

    const tasks = Array.from({ length: 12 }, (_, index) => async () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise((resolve) => setTimeout(resolve, 5));
      active -= 1;
      return index;
    });

    const result = await runWithConcurrencyLimit(tasks, 4);

    expect(result).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
    expect(maxActive).toBeLessThanOrEqual(4);
  });
});
