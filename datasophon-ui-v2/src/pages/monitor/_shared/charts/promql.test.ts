import { describe, expect, it } from 'vitest';
import {
  mergeNamedSeries,
  type PrometheusMatrix,
  type PrometheusVector,
  vectorToScalar,
} from './promql';

function vector(values: string[]): PrometheusVector {
  return {
    resultType: 'vector',
    result: values.map((v, i) => ({
      metric: { instance: `node-${i}:9100` },
      value: [1718000000, v] as [number, string],
    })),
  };
}

function matrix(
  rows: Array<{ metric: Record<string, string>; values: Array<[number, string]> }>,
): PrometheusMatrix {
  return { resultType: 'matrix', result: rows };
}

describe('vectorToScalar', () => {
  it('returns the value for a single-element vector', () => {
    expect(vectorToScalar(vector(['42']))).toBe(42);
  });

  it('returns NaN for an empty vector (no data != zero)', () => {
    // 缺失 series 必须可与真实 0 区分,交由展示层渲染为 '–'。
    expect(vectorToScalar(vector([]))).toBeNaN();
  });

  it('sums all elements for an unaggregated multi-instance vector', () => {
    // 旧实现只取 result[0],多实例 QPS/连接数等会少报;现按值求和。
    expect(vectorToScalar(vector(['10', '20', '30']))).toBe(60);
  });

  it('propagates NaN when Prometheus returns the string "NaN" (e.g. 0/0)', () => {
    expect(vectorToScalar(vector(['NaN']))).toBeNaN();
  });
});

describe('mergeNamedSeries', () => {
  it('单条原始 series（无 groupBy）时沿用 query 级 label，行为与此前一致', () => {
    const points = mergeNamedSeries([
      {
        label: 'Request',
        matrix: matrix([
          { metric: { instance: 'a:1', job: 'x' }, values: [[1000, '10']] },
        ]),
      },
      {
        label: 'Response',
        matrix: matrix([
          { metric: { instance: 'a:1', job: 'x' }, values: [[1000, '20']] },
        ]),
      },
    ]);
    expect(points.map((p) => p.series)).toEqual(['Request', 'Response']);
  });

  it('groupBy 产生多条原始 series 时按标签值区分，不再压扁成同名线', () => {
    // 复现 Codex 复审发现的 bug：RustFS R06 按 op 分组，此前全部落到同一个 series: 'op'。
    const points = mergeNamedSeries([
      {
        label: 'op',
        matrix: matrix([
          {
            metric: { instance: 'a:1', job: 'x', op: 'PutObject' },
            values: [[1000, '5']],
          },
          {
            metric: { instance: 'a:1', job: 'x', op: 'GetObject' },
            values: [[1000, '8']],
          },
        ]),
      },
    ]);
    const seriesNames = points.map((p) => p.series);
    expect(seriesNames).toContain('op (PutObject)');
    expect(seriesNames).toContain('op (GetObject)');
    expect(new Set(seriesNames).size).toBe(2);
  });

  it('多 query 共用同一 groupBy key 时，query label 前缀避免同值碰撞（R15 Used/Total by drive）', () => {
    const points = mergeNamedSeries([
      {
        label: 'Used',
        matrix: matrix([
          {
            metric: { instance: 'a:1', job: 'x', drive: '/data0' },
            values: [[1000, '1']],
          },
        ]),
      },
      {
        label: 'Total',
        matrix: matrix([
          {
            metric: { instance: 'a:1', job: 'x', drive: '/data0' },
            values: [[1000, '2']],
          },
        ]),
      },
    ]);
    const seriesNames = points.map((p) => p.series);
    expect(seriesNames).toContain('Used (/data0)');
    expect(seriesNames).toContain('Total (/data0)');
    // 若丢弃 query label 前缀，两条会都叫 "/data0" 而碰撞成一条线。
    expect(new Set(seriesNames).size).toBe(2);
  });
});
