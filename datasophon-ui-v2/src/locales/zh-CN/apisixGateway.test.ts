import { describe, expect, it } from 'vitest';
import enMessages from '../en-US/apisixGateway';
import messages from './apisixGateway';

describe('zh-CN Apisix gateway locale', () => {
  it('all keys exist and are non-empty', () => {
    const localeMessages: Record<string, string> = messages;
    for (const key of Object.keys(localeMessages)) {
      expect(key.startsWith('pages.apisixGateway.')).toBe(true);
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key].length).toBeGreaterThan(0);
    }
  });

  it('keys are fully aligned with en-US', () => {
    const zhKeys = Object.keys(messages).sort();
    const enKeys = Object.keys(enMessages).sort();
    expect(zhKeys).toEqual(enKeys);
  });
});
