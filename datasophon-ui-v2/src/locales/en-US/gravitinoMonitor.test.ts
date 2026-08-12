import { describe, expect, it } from 'vitest';
import messages from './gravitinoMonitor';

describe('en-US Gravitino monitor locale', () => {
  it('contains all keys used by the Gravitino dashboard', () => {
    const requiredKeys = [
      'pages.gravitinoMonitor.title',
      'pages.gravitinoMonitor.toolbar.instance',
      ...Array.from(
        { length: 20 },
        (_, index) =>
          `pages.gravitinoMonitor.panel.G${String(index + 1).padStart(2, '0')}`,
      ),
    ];
    for (const key of requiredKeys) {
      expect(messages[key as keyof typeof messages]).toBeTypeOf('string');
      expect(messages[key as keyof typeof messages]).not.toHaveLength(0);
    }
  });
});
