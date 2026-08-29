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

  it('applies submitted values for hidden fields marked configurableInWizard', () => {
    const configs = [
      {
        name: 'dfs.namenode.rpc-address.nn1',
        type: 'input',
        hidden: true,
        configurableInWizard: true,
        required: true,
        value: '',
      },
      {
        name: 'aws.s3.access.key.secret',
        type: 'input',
        hidden: true,
        required: true,
        value: 'platform-secret',
      },
      {
        name: 'disabled.option',
        label: 'Disabled option',
        type: 'input',
        enabled: false,
        required: false,
        value: 'platform-value',
      },
    ] as DATASOPHON.ConfigField[];

    const result = invokeFormatTemplateData(configs, {
      'dfs.namenode.rpc-address.nn1': 'host1:8020',
      'disabled.option': 'unexpected-value',
    });

    expect(result).toEqual([
      expect.objectContaining({
        name: 'dfs.namenode.rpc-address.nn1',
        value: 'host1:8020',
      }),
      expect.objectContaining({
        name: 'aws.s3.access.key.secret',
        value: 'platform-secret',
      }),
      expect.objectContaining({
        name: 'disabled.option',
        value: 'platform-value',
      }),
    ]);
  });
});
