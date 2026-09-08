import { render, screen } from '@testing-library/react';
import { message } from 'antd';
import { beforeEach, expect, it, vi } from 'vitest';
import {
  assignNodeLabel,
  listNodeLabels,
  saveNodeLabel,
} from '@/services/label';
import AssignLabelModal from './AssignLabelModal';

const form = vi.hoisted(() => ({
  finish: undefined as unknown as (v: {
    nodeLabel: string;
  }) => Promise<boolean>,
}));
vi.mock('@umijs/max', () => ({
  useRequest: () => ({ data: [], refresh: vi.fn() }),
}));
vi.mock('@/services/label', () => ({
  assignNodeLabel: vi.fn(),
  listNodeLabels: vi.fn(),
  saveNodeLabel: vi.fn(),
}));
vi.mock('antd', () => {
  const message = { success: vi.fn(), error: vi.fn() };
  return { message, App: { useApp: () => ({ message }) } };
});
vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ onFinish, children }: any) => {
    form.finish = onFinish;
    return children;
  },
  ProFormSelect: ({ options }: any) => (
    <>
      {options.map((option: any) => (
        <span key={option.value}>{option.label}</span>
      ))}
    </>
  ),
}));
const label = { id: 8, nodeLabel: 'doris', clusterId: 1 };
const success = vi.fn();
function setup() {
  render(
    <AssignLabelModal
      clusterId={1}
      hostIds={[2, 3]}
      trigger={<span>分配</span>}
      onSuccess={success}
    />,
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listNodeLabels).mockResolvedValue({ data: [label] });
  vi.mocked(saveNodeLabel).mockResolvedValue({ data: undefined });
  vi.mocked(assignNodeLabel).mockResolvedValue({ data: undefined });
});
it('shows builtin choices without creating them and reuses an existing label', async () => {
  setup();
  for (const name of [
    '前置区（edge）',
    'Kubernetes集群（kubernetes）',
    'Doris集群（doris）',
    'VM集群（vm）',
  ])
    expect(screen.getByText(name)).toBeTruthy();
  expect(saveNodeLabel).not.toHaveBeenCalled();
  expect(await form.finish({ nodeLabel: 'doris' })).toBe(true);
  expect(saveNodeLabel).not.toHaveBeenCalled();
  expect(assignNodeLabel).toHaveBeenCalledWith(1, 8, [2, 3]);
});
it('creates missing builtin and uses its persisted ID', async () => {
  vi.mocked(listNodeLabels)
    .mockResolvedValueOnce({ data: [] })
    .mockResolvedValueOnce({ data: [label] });
  setup();
  expect(await form.finish({ nodeLabel: 'doris' })).toBe(true);
  expect(saveNodeLabel).toHaveBeenCalledWith(1, 'doris');
  expect(assignNodeLabel).toHaveBeenCalledWith(1, 8, [2, 3]);
});
it('stops on creation business failure', async () => {
  vi.mocked(listNodeLabels).mockResolvedValue({ data: [] });
  vi.mocked(saveNodeLabel).mockResolvedValue({
    data: undefined,
    success: false,
    errorMessage: '创建失败',
  } as any);
  setup();
  expect(await form.finish({ nodeLabel: 'doris' })).toBe(false);
  expect(assignNodeLabel).not.toHaveBeenCalled();
  expect(success).not.toHaveBeenCalled();
  expect(message.error).toHaveBeenCalledWith('创建失败');
});
it('does not report failed assignment as success', async () => {
  vi.mocked(assignNodeLabel).mockResolvedValue({
    data: undefined,
    success: false,
    errorMessage: '分配失败',
  } as any);
  setup();
  expect(await form.finish({ nodeLabel: 'doris' })).toBe(false);
  expect(success).not.toHaveBeenCalled();
  expect(message.success).not.toHaveBeenCalled();
});
