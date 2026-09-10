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
const request = vi.hoisted(() => ({
  data: [] as { id: number; nodeLabel: string; clusterId: number }[],
  fetcher: undefined as unknown as () => Promise<{ data: unknown[] }>,
}));
vi.mock('@umijs/max', () => ({
  useRequest: (fn: () => Promise<{ data: unknown[] }>) => {
    request.fetcher = fn;
    return { data: request.data, refresh: vi.fn() };
  },
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
  request.data = [label];
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
  // 已加载的标签列表里已经有这个标签，不应该再多打一次网络请求去重新确认。
  expect(listNodeLabels).not.toHaveBeenCalled();
  expect(assignNodeLabel).toHaveBeenCalledWith(1, 8, [2, 3]);
});
it('creates missing builtin and uses its persisted ID', async () => {
  request.data = [];
  vi.mocked(listNodeLabels).mockResolvedValue({ data: [label] });
  setup();
  expect(await form.finish({ nodeLabel: 'doris' })).toBe(true);
  expect(saveNodeLabel).toHaveBeenCalledWith(1, 'doris');
  expect(listNodeLabels).toHaveBeenCalledTimes(1);
  expect(assignNodeLabel).toHaveBeenCalledWith(1, 8, [2, 3]);
});
it('stops on creation business failure', async () => {
  request.data = [];
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
it('falls back to an empty label list when the API returns a non-array data field', async () => {
  vi.mocked(listNodeLabels).mockResolvedValueOnce({ data: {} as any });
  setup();
  await expect(request.fetcher()).resolves.toEqual({ data: [] });
});
