import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DorisActiveTask from './index';
import type { DorisActiveTaskResponse } from './types';

const { pollingOptions, pollingState } = vi.hoisted(() => ({
  pollingOptions: [] as Array<{ autoRefresh?: boolean }>,
  pollingState: {
    data: undefined as DorisActiveTaskResponse | undefined,
    error: undefined as unknown,
    loading: false,
    lastUpdatedAt: undefined as number | undefined,
    refresh: vi.fn(),
  },
}));

vi.mock('./hooks/useActiveTaskPolling', () => ({
  useActiveTaskPolling: (options: { autoRefresh?: boolean }) => {
    pollingOptions.push(options);
    return pollingState;
  },
}));

vi.mock('./service', () => ({
  getDorisActiveTasks: vi.fn(),
  getDorisActiveTaskDetail: vi.fn(),
}));

vi.mock('./ActiveTaskTable', () => ({
  default: ({ tasks }: { tasks: Array<{ taskId: string }> }) => (
    <div data-testid="doris-active-task-table">
      {tasks.map((task) => task.taskId)}
    </div>
  ),
}));

vi.mock('./TaskDetailDrawer', () => ({
  default: () => null,
}));

const response = (overrides: Partial<DorisActiveTaskResponse> = {}) => ({
  tasks: [{ taskId: 'q-1', type: 'QUERY' }],
  degraded: false,
  partialFailures: [],
  truncated: false,
  sourceTruncated: false,
  total: 1,
  returned: 1,
  connectedHostPort: 'ddh-01:9030',
  ...overrides,
});

describe('DorisActiveTask page assembly', () => {
  beforeEach(() => {
    pollingOptions.length = 0;
    pollingState.data = response();
    pollingState.error = undefined;
    pollingState.loading = false;
    pollingState.lastUpdatedAt = undefined;
    pollingState.refresh.mockClear();
  });

  it('starts in manual mode, displays the actual endpoint, and clears the table on failure', async () => {
    const view = render(<DorisActiveTask clusterId={7} instanceId={8} />);

    expect(
      screen.getByTestId('doris-active-task-connected-host'),
    ).toHaveTextContent('ddh-01:9030');
    expect(screen.getByTestId('doris-active-task-table')).toHaveTextContent(
      'q-1',
    );
    expect(pollingOptions[0]).toMatchObject({ autoRefresh: false });

    fireEvent.click(screen.getByRole('switch', { name: '10 秒自动刷新' }));
    await waitFor(() =>
      expect(pollingOptions.at(-1)).toMatchObject({ autoRefresh: true }),
    );

    pollingState.error = new Error('raw backend detail');
    view.rerender(<DorisActiveTask clusterId={7} instanceId={8} />);

    await waitFor(() =>
      expect(screen.getByTestId('doris-active-task-status')).toHaveAttribute(
        'data-status',
        'failed',
      ),
    );
    expect(
      screen.queryByTestId('doris-active-task-table'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('raw backend detail')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(pollingOptions.at(-1)).toMatchObject({ autoRefresh: false }),
    );
  });

  it('keeps empty and degraded page states visually distinct', () => {
    const view = render(<DorisActiveTask clusterId={7} instanceId={8} />);

    pollingState.data = response({ tasks: [], total: 0, returned: 0 });
    view.rerender(<DorisActiveTask clusterId={7} instanceId={8} />);
    expect(screen.getByTestId('doris-active-task-status')).toHaveAttribute(
      'data-status',
      'empty',
    );

    pollingState.data = response({ degraded: true });
    view.rerender(<DorisActiveTask clusterId={7} instanceId={8} />);
    expect(
      screen.getByText('当前以只读账号连接，部分字段不可用'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('doris-active-task-status')).not.toHaveAttribute(
      'data-status',
      'empty',
    );
  });
});
