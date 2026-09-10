/// <reference types="vite/client" />

import { describe, expect, it } from 'vitest';

type Messages = Record<string, string>;

const isMessageModule = (path: string) => !path.includes('.test.');

const zhModules = import.meta.glob<Messages>('./zh-CN/*.ts', {
  eager: true,
  import: 'default',
});
const enModules = import.meta.glob<Messages>('./en-US/*.ts', {
  eager: true,
  import: 'default',
});

const moduleNames = Object.keys(zhModules)
  .filter(isMessageModule)
  .map((path) => path.replace('./zh-CN/', ''))
  .sort();

describe('locales 语言包一致性', () => {
  it('zh-CN 与 en-US 的模块文件一一对应', () => {
    const enModuleNames = Object.keys(enModules)
      .filter(isMessageModule)
      .map((path) => path.replace('./en-US/', ''))
      .sort();

    expect(enModuleNames).toEqual(moduleNames);
  });

  describe.each(moduleNames)('%s', (moduleName) => {
    const zh = zhModules[`./zh-CN/${moduleName}`];
    const en = enModules[`./en-US/${moduleName}`];

    it('两侧 key 集合完全一致', () => {
      expect(Object.keys(en).sort()).toEqual(Object.keys(zh).sort());
    });

    it('两侧文案均为非空字符串', () => {
      const locales: ReadonlyArray<readonly [string, Messages]> = [
        ['zh-CN', zh],
        ['en-US', en],
      ];

      for (const [locale, messages] of locales) {
        for (const [key, value] of Object.entries(messages)) {
          expect(value, `${locale} ${key}`).toBeTypeOf('string');
          expect(value, `${locale} ${key}`).not.toHaveLength(0);
        }
      }
    });
  });
});
