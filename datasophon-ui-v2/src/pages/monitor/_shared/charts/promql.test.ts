import { describe, expect, it } from 'vitest';
import { type PrometheusVector, vectorToScalar } from './promql';

function vector(values: string[]): PrometheusVector {
  return {
    resultType: 'vector',
    result: values.map((v, i) => ({
      metric: { instance: `node-${i}:9100` },
      value: [1718000000, v] as [number, string],
    })),
  };
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
