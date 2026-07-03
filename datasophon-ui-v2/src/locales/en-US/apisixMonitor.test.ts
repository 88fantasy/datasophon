import { describe, expect, it } from 'vitest';
import messages from './apisixMonitor';

describe('en-US Apisix monitor locale', () => {
  it('contains page title and all panel titles', () => {
    const localeMessages: Record<string, string> = messages;
    const panelIds = [
      'A01',
      'A02',
      'A03',
      'A04',
      'A05',
      'A06',
      'A07',
      'A08',
      'A09',
      'A10',
      'A11',
      'A12',
    ];
    const requiredKeys = [
      'pages.apisixMonitor.title',
      ...panelIds.map((id) => `pages.apisixMonitor.panel.${id}`),
    ];

    for (const key of requiredKeys) {
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key]).not.toHaveLength(0);
    }
  });
});
