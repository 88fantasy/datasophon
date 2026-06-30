import { describe, expect, it } from 'vitest';
import { isReadonlyOtelBuiltinRule } from './otelBuiltinRules';

describe('isReadonlyOtelBuiltinRule', () => {
  it('locks Nexus and Doris OTel built-in alert rules', () => {
    expect(isReadonlyOtelBuiltinRule({ alertQuotaName: 'Nexus实例只读' })).toBe(true);
    expect(isReadonlyOtelBuiltinRule({ alertQuotaName: 'Doris查询错误率' })).toBe(true);
  });

  it('does not lock ordinary alert rules or new records', () => {
    expect(isReadonlyOtelBuiltinRule({ alertQuotaName: '主机CPU使用率' })).toBe(false);
    expect(isReadonlyOtelBuiltinRule(null)).toBe(false);
    expect(isReadonlyOtelBuiltinRule(undefined)).toBe(false);
  });
});
