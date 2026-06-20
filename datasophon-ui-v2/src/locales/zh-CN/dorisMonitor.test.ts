import { describe, expect, it } from 'vitest';
import messages from './dorisMonitor';

describe('zh-CN Doris monitor locale', () => {
  it('contains page, toolbar, sections, and all panel titles', () => {
    const localeMessages: Record<string, string> = messages;
    const panelIds = [
      'DO-A01',
      'DO-A02',
      'DO-A03',
      'DO-A04',
      'DO-A05',
      'DO-A06',
      'DO-A07',
      'DO-A08',
      'DO-A09',
      'DO-B01',
      'DO-B02',
      'DO-B03',
      'DO-B04',
      'DO-B05',
      'DO-B06',
      'DO-B07',
      'DO-B08',
      'DO-B09',
      'DO-B10',
      'DO-B11',
      'DO-B12',
      'DO-C01',
      'DO-C02',
      'DO-C03',
      'DO-C04',
      'DO-C05',
      'DO-C06',
      'DO-C07',
      'DO-C08',
      'DO-C09',
      'DO-C10',
      'DO-C11',
    ];
    const requiredKeys = [
      'pages.dorisMonitor.title',
      'pages.dorisMonitor.toolbar.cluster',
      'pages.dorisMonitor.toolbar.feInstance',
      'pages.dorisMonitor.toolbar.beInstance',
      'pages.dorisMonitor.toolbar.rateInterval',
      'pages.dorisMonitor.toolbar.notice',
      'pages.dorisMonitor.section.cluster',
      'pages.dorisMonitor.section.cluster.subtitle',
      'pages.dorisMonitor.section.fe',
      'pages.dorisMonitor.section.fe.subtitle',
      'pages.dorisMonitor.section.be',
      'pages.dorisMonitor.section.be.subtitle',
      ...panelIds.map((id) => `pages.dorisMonitor.panel.${id}`),
    ];

    for (const key of requiredKeys) {
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key]).not.toHaveLength(0);
    }
  });
});
