import { describe, expect, it } from 'vitest';
import { matrixToLatestScalar } from './useDorisDashboardData';

describe('matrixToLatestScalar', () => {
  it('sums all series from the latest shared time bucket', () => {
    expect(
      matrixToLatestScalar({
        resultType: 'matrix',
        result: [
          {
            metric: { instance: 'one' },
            values: [
              [10, '1'],
              [20, '2'],
            ],
          },
          {
            metric: { instance: 'two' },
            values: [
              [10, '3'],
              [20, '4'],
            ],
          },
        ],
      }),
    ).toBe(6);
  });

  it('returns NaN when the range query has no samples', () => {
    expect(
      matrixToLatestScalar({ resultType: 'matrix', result: [] }),
    ).toBeNaN();
  });
});
