import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createCluster } from '@/services/cluster';
import BuildOrEditModal from './BuildOrEditModal';

vi.mock('@umijs/max', () => ({
  useRequest: () => ({
    data: [{ id: 3, frameCode: 'datacluster-k8s' }],
  }),
}));

vi.mock('@/services/cluster', () => ({
  createCluster: vi.fn(),
  listFrames: vi.fn(),
  updateCluster: vi.fn(),
}));

/**
 * ModalForm 与 ProForm* 组件在 jsdom 下渲染成本高，这里替换成能暴露
 * 「字段名 → 当前值」与 onFinish 的最小实现，专注验证接管模式的表单逻辑。
 */
vi.mock('@ant-design/pro-components', () => {
  let formValues: Record<string, unknown> = {};

  const Field = ({
    name,
    label,
    options,
  }: {
    name: string;
    label?: string;
    options?: Array<{ label: string; value: string }>;
  }) => (
    <div data-testid={`field-${name}`}>
      <span>{label}</span>
      {(options ?? []).map((o) => (
        <button
          key={o.value}
          type="button"
          data-testid={`opt-${name}-${o.value}`}
          onClick={() => {
            formValues[name] = o.value;
          }}
        >
          {o.label}
        </button>
      ))}
    </div>
  );

  return {
    ModalForm: ({
      children,
      initialValues,
      onFinish,
      trigger,
    }: {
      children: ReactNode;
      initialValues?: Record<string, unknown>;
      onFinish?: (v: Record<string, unknown>) => Promise<boolean>;
      trigger?: ReactNode;
    }) => {
      if (!Object.keys(formValues).length) {
        formValues = { ...(initialValues ?? {}) };
      }
      return (
        <div>
          {trigger}
          <div data-testid="initial-values">{JSON.stringify(initialValues)}</div>
          {children}
          <button type="button" onClick={() => onFinish?.(formValues)}>
            提交
          </button>
        </div>
      );
    },
    ProFormText: Field,
    ProFormSelect: Field,
    ProFormRadio: Object.assign(Field, { Group: Field }),
    // 依赖组件在测试里直接渲染子节点，等价于「条件全部命中」
    ProFormDependency: ({
      children,
    }: {
      children: (v: Record<string, unknown>) => ReactNode;
    }) => <>{children(formValues)}</>,
  };
});

describe('BuildOrEditModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('defaults to MANAGED so ordinary cluster creation is unaffected', async () => {
    render(<BuildOrEditModal trigger={<span>新建</span>} onSuccess={vi.fn()} />);

    expect(screen.getByTestId('initial-values').textContent).toContain(
      '"manageMode":"MANAGED"',
    );
  });

  it('submits manageMode=IMPORTED after choosing takeover', async () => {
    vi.mocked(createCluster).mockResolvedValue({} as never);
    const { rerender } = render(
      <BuildOrEditModal trigger={<span>新建</span>} onSuccess={vi.fn()} />,
    );

    // 选 K8s 集群后重渲染，创建方式字段才会由 ProFormDependency 渲染出来
    fireEvent.click(screen.getByTestId('opt-archType-k8s'));
    rerender(<BuildOrEditModal trigger={<span>新建</span>} onSuccess={vi.fn()} />);
    fireEvent.click(screen.getByTestId('opt-manageMode-IMPORTED'));
    fireEvent.click(screen.getByText('提交'));

    await waitFor(() => {
      expect(createCluster).toHaveBeenCalledWith(
        expect.objectContaining({ manageMode: 'IMPORTED', archType: 'k8s' }),
      );
    });
  });
});
