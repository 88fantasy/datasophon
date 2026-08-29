import { describe, expect, it } from 'vitest';
import { formatDsTime } from './formatters';

describe('formatDsTime', () => {
  it('renders a DS local-time string as-is, without an extra UTC offset', () => {
    // DS Open API 返回的是服务器本地时间，无时区后缀；不应再被当作 UTC 转一次本地
    // （曾经的实现会因此多算 8 小时，见 datasophon-api 的时区污染修复）。
    expect(formatDsTime('2026-08-27T12:27:34')).toBe('2026-08-27 12:27:34');
  });

  it('returns an em dash for missing or invalid values', () => {
    expect(formatDsTime(undefined)).toBe('—');
    expect(formatDsTime('not-a-time')).toBe('—');
  });
});
