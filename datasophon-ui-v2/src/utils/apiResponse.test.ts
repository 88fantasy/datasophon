import { describe, expect, it } from 'vitest';
import { getApiFailureMessage } from './apiResponse';

describe('getApiFailureMessage', () => {
  it('returns undefined for successful v2 responses', () => {
    expect(getApiFailureMessage({ success: true, data: null })).toBeUndefined();
  });

  it('returns errorMessage for failed v2 responses', () => {
    expect(
      getApiFailureMessage({
        success: false,
        errorMessage: '保存失败',
      }),
    ).toBe('保存失败');
  });

  it('returns msg for failed legacy Result responses', () => {
    expect(
      getApiFailureMessage({
        code: 500,
        msg: 'No static resource api/v2/cluster/k8sConfig/saveOrUpdateConfig.',
      }),
    ).toBe('No static resource api/v2/cluster/k8sConfig/saveOrUpdateConfig.');
  });

  it('treats empty responses as failures', () => {
    expect(getApiFailureMessage(undefined, '配置保存失败')).toBe(
      '配置保存失败',
    );
  });
});
