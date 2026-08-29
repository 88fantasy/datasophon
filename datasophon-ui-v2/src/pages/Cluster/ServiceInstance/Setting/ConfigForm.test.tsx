import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ConfigForm from './ConfigForm';

interface MockFormItemProps {
  label?: React.ReactNode;
}

vi.mock('@ant-design/pro-components', () => ({
  ProCard: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
  ProFormGroup: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
  ProFormList: ({ label }: MockFormItemProps) => <div>{label}</div>,
  ProFormSelect: ({ label }: MockFormItemProps) => <div>{label}</div>,
  ProFormSlider: ({ label }: MockFormItemProps) => <div>{label}</div>,
  ProFormSwitch: ({ label }: MockFormItemProps) => <div>{label}</div>,
  ProFormText: ({ label }: MockFormItemProps) => <div>{label}</div>,
}));

vi.mock('@ant-design/icons', () => ({
  QuestionCircleOutlined: () => null,
}));

function field(
  name: string,
  overrides: Partial<DATASOPHON.ConfigField> = {},
): DATASOPHON.ConfigField {
  // label 与 name 会渲染在同一行的两个 span 里，用可区分的 label 避免查询命中多个节点
  return {
    name,
    label: `${name} 标签`,
    value: '',
    required: false,
    enabled: true,
    type: 'input',
    ...overrides,
  };
}

describe('ConfigForm 渲染过滤', () => {
  it('保留 hidden 但可配置的参数，运维仍能在设置页修改', () => {
    // 各服务 DDL 里 hidden 的参数绝大多数同时标了 configurableInWizard。
    render(
      <ConfigForm
        templateData={[
          field('fs.trash.interval', {
            hidden: true,
            configurableInWizard: true,
          }),
          field('advertised.listeners', {
            hidden: true,
            configurableInWizard: true,
          }),
        ]}
      />,
    );

    expect(screen.getByText('fs.trash.interval 标签')).toBeTruthy();
    expect(screen.getByText('advertised.listeners 标签')).toBeTruthy();
  });

  it('隐藏平台托管的参数（hidden 且非 configurableInWizard）', () => {
    render(
      <ConfigForm
        templateData={[
          field('aws.s3.access.key.secret', {
            hidden: true,
            configurableInWizard: false,
          }),
          field('aws.s3.bucket.name'),
        ]}
      />,
    );

    expect(screen.queryByText('aws.s3.access.key.secret 标签')).toBeNull();
    expect(screen.getByText('aws.s3.bucket.name 标签')).toBeTruthy();
  });

  it('enabled 为 false 的参数不渲染', () => {
    render(
      <ConfigForm
        templateData={[field('legacy.param', { enabled: false }), field('kept')]}
      />,
    );

    expect(screen.queryByText('legacy.param 标签')).toBeNull();
    expect(screen.getByText('kept 标签')).toBeTruthy();
  });
});
