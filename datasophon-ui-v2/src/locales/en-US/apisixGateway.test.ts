import { describe, expect, it } from 'vitest';
import messages from './apisixGateway';
import zhMessages from '../zh-CN/apisixGateway';

describe('en-US Apisix gateway locale', () => {
  it('all keys exist and are non-empty', () => {
    const localeMessages: Record<string, string> = messages;
    for (const key of Object.keys(localeMessages)) {
      expect(key.startsWith('pages.apisixGateway.')).toBe(true);
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key].length).toBeGreaterThan(0);
    }
  });

  it('keys are fully aligned with zh-CN', () => {
    const enKeys = Object.keys(messages).sort();
    const zhKeys = Object.keys(zhMessages).sort();
    expect(enKeys).toEqual(zhKeys);
  });
});
