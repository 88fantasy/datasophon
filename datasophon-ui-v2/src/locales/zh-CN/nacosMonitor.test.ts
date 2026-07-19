import { describe, expect, it } from 'vitest';
import messages from './nacosMonitor';

describe('zh-CN Nacos monitor locale', () => {
  it('contains all keys used by the Nacos dashboard', () => {
    const requiredKeys = [
      'pages.nacosMonitor.title',
      'pages.nacosMonitor.toolbar.instance',
      ...Array.from(
        { length: 18 },
        (_, index) =>
          `pages.nacosMonitor.panel.N${String(index + 1).padStart(2, '0')}`,
      ),
    ];
    for (const key of requiredKeys) {
      expect(messages[key as keyof typeof messages]).toBeTypeOf('string');
      expect(messages[key as keyof typeof messages]).not.toHaveLength(0);
    }
  });
});
