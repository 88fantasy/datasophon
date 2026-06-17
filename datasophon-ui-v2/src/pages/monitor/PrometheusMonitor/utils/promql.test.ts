import { describe, expect, it } from 'vitest';
import {
  deriveInstancesAndJobs,
  matrixToSeries,
  mergeNamedSeries,
  replaceVars,
  vectorToScalar,
  vectorToTableRows,
} from './promql';

describe('PrometheusMonitor promql helpers', () => {
  it('replaces dashboard variables with regex-safe defaults', () => {
    const query =
      'avg_over_time(up{instance=~"$instance",job=~"$job"}[$interval])';

    expect(
      replaceVars(query, {
        instance: '',
        job: '',
        interval: '',
      }),
    ).toBe('avg_over_time(up{instance=~".+",job=~".+"}[5m])');
  });

  it('replaces selected instance, job and interval values', () => {
    const query =
      'sum(sum_over_time(metric{instance=~"$instance",job=~"$job"}[$interval]))';

    expect(
      replaceVars(query, {
        instance: '10.0.0.1:9090|10.0.0.2:9090',
        job: 'prometheus',
        interval: '15m',
      }),
    ).toBe(
      'sum(sum_over_time(metric{instance=~"10.0.0.1:9090|10.0.0.2:9090",job=~"prometheus"}[15m]))',
    );
  });

  it('converts vector results to table rows', () => {
    expect(
      vectorToTableRows({
        resultType: 'vector',
        result: [
          {
            metric: { instance: 'prometheus:9090', job: 'prometheus' },
            value: [1710000000, '0'],
          },
        ],
      }),
    ).toEqual([
      {
        instance: 'prometheus:9090',
        job: 'prometheus',
        value: 0,
        key: 'prometheus:9090-prometheus',
      },
    ]);
  });

  // ── 新增转换函数测试 ──────────────────────────────────────────────────────

  describe('matrixToSeries', () => {
    it('converts matrix result to TimeSeriesPoint[] using specified seriesKey', () => {
      const matrix = {
        resultType: 'matrix' as const,
        result: [
          {
            metric: { instance: 'localhost:9090', job: 'prometheus' },
            values: [
              [1718000000, '1.5'] as [number, string],
              [1718000030, '2.0'] as [number, string],
            ],
          },
        ],
      };
      const points = matrixToSeries(matrix, 'instance');
      expect(points).toHaveLength(2);
      expect(points[0]).toEqual({
        time: 1718000000000,
        value: 1.5,
        series: 'localhost:9090',
      });
      expect(points[1]).toEqual({
        time: 1718000030000,
        value: 2.0,
        series: 'localhost:9090',
      });
    });

    it('falls back to first non-reserved label when seriesKey absent', () => {
      const matrix = {
        resultType: 'matrix' as const,
        result: [
          {
            metric: { scrape_job: 'node', instance: 'h:9090' },
            values: [[1718000000, '3'] as [number, string]],
          },
        ],
      };
      const points = matrixToSeries(matrix);
      // scrape_job 不在保留集合中，应取 'node'
      expect(points[0].series).toBe('node');
    });

    it('returns empty array for empty matrix', () => {
      expect(matrixToSeries({ resultType: 'matrix', result: [] })).toEqual([]);
    });
  });

  describe('mergeNamedSeries', () => {
    it('merges multiple labeled matrices into single series array', () => {
      const m1 = {
        resultType: 'matrix' as const,
        result: [
          { metric: {}, values: [[1718000000, '1'] as [number, string]] },
        ],
      };
      const m2 = {
        resultType: 'matrix' as const,
        result: [
          { metric: {}, values: [[1718000000, '2'] as [number, string]] },
        ],
      };
      const result = mergeNamedSeries([
        { label: 'created', matrix: m1 },
        { label: 'removed', matrix: m2 },
      ]);
      expect(result).toHaveLength(2);
      expect(result.find((p) => p.series === 'created')?.value).toBe(1);
      expect(result.find((p) => p.series === 'removed')?.value).toBe(2);
    });
  });

  describe('vectorToScalar', () => {
    it('returns numeric value from first result', () => {
      const v = {
        resultType: 'vector' as const,
        result: [
          { metric: {}, value: [1718000000, '99.8'] as [number, string] },
        ],
      };
      expect(vectorToScalar(v)).toBeCloseTo(99.8);
    });

    it('returns 0 for empty vector', () => {
      expect(vectorToScalar({ resultType: 'vector', result: [] })).toBe(0);
    });
  });

  describe('deriveInstancesAndJobs', () => {
    it('extracts distinct instances and jobs from up vector', () => {
      const v = {
        resultType: 'vector' as const,
        result: [
          {
            metric: { instance: 'h1:9090', job: 'prometheus' },
            value: [1718000000, '1'] as [number, string],
          },
          {
            metric: { instance: 'h2:9090', job: 'node' },
            value: [1718000000, '1'] as [number, string],
          },
          {
            metric: { instance: 'h1:9090', job: 'node' },
            value: [1718000000, '1'] as [number, string],
          },
        ],
      };
      const { instances, jobs } = deriveInstancesAndJobs(v);
      expect(instances.sort()).toEqual(['h1:9090', 'h2:9090']);
      expect(jobs.sort()).toEqual(['node', 'prometheus']);
    });

    it('returns empty arrays for empty vector', () => {
      const { instances, jobs } = deriveInstancesAndJobs({
        resultType: 'vector',
        result: [],
      });
      expect(instances).toEqual([]);
      expect(jobs).toEqual([]);
    });
  });
});
