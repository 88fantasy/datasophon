import { describe, expect, it } from 'vitest';
import { runWithConcurrencyLimit } from '../../_shared/useDashboardData';

describe('runWithConcurrencyLimit', () => {
  it('runs tasks without exceeding the configured concurrency', async () => {
    let active = 0;
    let maxActive = 0;

    const tasks = Array.from({ length: 10 }, (_, index) => async () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise((resolve) => setTimeout(resolve, 5));
      active -= 1;
      return index;
    });

    const result = await runWithConcurrencyLimit(tasks, 3);

    expect(result).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
    expect(maxActive).toBeLessThanOrEqual(3);
  });
});
