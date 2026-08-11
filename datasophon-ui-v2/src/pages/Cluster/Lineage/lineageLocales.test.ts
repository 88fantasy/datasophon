import { describe, expect, it } from 'vitest';

import enUSLineage from '../../../locales/en-US/lineage';
import zhCNLineage from '../../../locales/zh-CN/lineage';

const TOOLTIP_KEYS = [
  'pages.lineage.tooltip.tableName',
  'pages.lineage.tooltip.canonicalName',
  'pages.lineage.tooltip.connector',
  'pages.lineage.tooltip.catalog',
  'pages.lineage.tooltip.database',
  'pages.lineage.tooltip.dwLayer',
];

describe('lineage tooltip locale contract', () => {
  it.each([
    ['en-US', enUSLineage],
    ['zh-CN', zhCNLineage],
  ])('%s defines every tooltip message', (_locale, messages) => {
    for (const key of TOOLTIP_KEYS) {
      expect(messages[key as keyof typeof messages]).toBeTruthy();
    }
  });
});
