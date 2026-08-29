import { describe, expect, it } from 'vitest';
import { classifyDsError } from './errors';

describe('classifyDsError', () => {
  it('distinguishes missing token, invalid token, and unavailable DS', () => {
    expect(
      classifyDsError({
        info: { errorCode: 400, errorMessage: '请在 DS 服务配置中填写 apiToken' },
      }),
    ).toBe('tokenMissing');
    expect(
      classifyDsError({
        response: {
          status: 401,
          data: { errorMessage: 'DS apiToken 已失效' },
        },
      }),
    ).toBe('tokenInvalid');
    expect(
      classifyDsError({
        response: {
          status: 502,
          data: { errorMessage: 'DS Open API 不可达或请求超时' },
        },
      }),
    ).toBe('unavailable');
  });
});
