import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import TaskDetailDrawer from './TaskDetailDrawer';
import type { DorisActiveTask } from './types';

const query: DorisActiveTask = {
  taskId: 'q-1',
  type: 'QUERY',
  user: 'alice',
  sql: 'select * from orders',
  truncated: true,
  beDetails: [
    {
      beId: 'be-1',
      peakMemoryBytes: 20,
      currentMemoryBytes: 10,
      scanRows: 3,
      scanBytes: 30,
    },
  ],
};

const load: DorisActiveTask = {
  taskId: 'load-1',
  type: 'LOAD',
  beDetails: query.beDetails,
};

describe('TaskDetailDrawer', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows BE resources, warns about SQL truncation, and copies returned SQL only', () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });

    render(<TaskDetailDrawer task={query} open onClose={vi.fn()} />);

    expect(screen.getByText('be-1')).toBeInTheDocument();
    expect(
      screen.getByText('SQL 已截断，复制内容仅包含当前返回内容'),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '复制当前返回内容' }));
    expect(writeText).toHaveBeenCalledWith(query.sql);
  });

  it('keeps resource details for Load without exposing an SQL drawer', () => {
    render(<TaskDetailDrawer task={load} open onClose={vi.fn()} />);

    expect(screen.getByText('be-1')).toBeInTheDocument();
    expect(
      screen.queryByTestId('doris-active-task-sql'),
    ).not.toBeInTheDocument();
  });
});
