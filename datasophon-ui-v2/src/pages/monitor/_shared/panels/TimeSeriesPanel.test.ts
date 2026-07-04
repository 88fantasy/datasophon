import { describe, expect, it } from 'vitest';
import { baseSeriesLabel } from './TimeSeriesPanel';

describe('baseSeriesLabel', () => {
  it('无后缀时原样返回（无 groupBy 场景，colorMap 精确匹配即可）', () => {
    expect(baseSeriesLabel('Used')).toBe('Used');
  });

  it('剥离 mergeNamedSeries 拼接的 " (值)" 后缀，恢复原始 query label', () => {
    // 对应 DorisMonitor DO-C03 / RustFS R15 的 colorMap 仍按 base label 声明的场景。
    expect(baseSeriesLabel('Used (/data0)')).toBe('Used');
    expect(baseSeriesLabel('local_used_pct (/data0)')).toBe('local_used_pct');
  });

  it('值本身含括号时只按第一个 " (" 切分', () => {
    expect(baseSeriesLabel('op (PutObject (v2))')).toBe('op');
  });
});
