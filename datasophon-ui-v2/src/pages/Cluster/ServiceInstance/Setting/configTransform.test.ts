import { describe, expect, it } from 'vitest';
import { invokeFormatTemplateData } from './configTransform';

describe('invokeFormatTemplateData', () => {
  it('preserves hidden platform-managed values when the form is submitted', () => {
    const configs = [
      {
        name: 'visible.option',
        type: 'input',
        hidden: false,
        value: 'before',
      },
      {
        name: 'aws.s3.access.key.secret',
        type: 'input',
        hidden: true,
        required: true,
        value: 'platform-secret',
      },
    ] as DATASOPHON.ConfigField[];

    const result = invokeFormatTemplateData(configs, {
      'visible.option': 'after',
    });

    expect(result).toEqual([
      expect.objectContaining({ name: 'visible.option', value: 'after' }),
      expect.objectContaining({
        name: 'aws.s3.access.key.secret',
        value: 'platform-secret',
      }),
    ]);
  });
});
