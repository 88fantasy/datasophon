import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import TaskDetailDrawer from './TaskDetailDrawer';
import type { DorisActiveTask } from './types';

const query: DorisActiveTask = {
  taskId: 'q-1',
  type: 'QUERY',
  user: 'alice',
  startTime: '2026-09-03 12:00:00',
  elapsedMs: 1234,
  currentMemoryBytes: 10,
  peakMemoryBytes: 20,
  scanRows: 42,
  scanBytes: 1_000_000,
  cpuTimeMs: 321,
  shuffleSendBytes: 2_000_000,
  shuffleSendRows: 43,
  spillWriteBytesToLocalStorage: 3_000_000,
  spillReadBytesFromLocalStorage: 4_000_000,
  sql: 'select * from orders',
  detailSql: 'select * from orders where customer_id = 42',
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
  beDetails: query.beDetails?.map((detail) => ({
    ...detail,
    scanBytes: null,
  })),
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
    expect(screen.getByText('2026-09-03 12:00:00')).toBeInTheDocument();
    expect(screen.getByText('1234 ms')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('976.6 KB')).toBeInTheDocument();
    expect(screen.getByText('1.9 MB')).toBeInTheDocument();
    expect(screen.getByText('2.9 MB')).toBeInTheDocument();
    expect(screen.getByText('3.8 MB')).toBeInTheDocument();
    expect(
      screen.getByText('SQL 已截断，复制内容仅包含当前返回内容'),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '复制当前返回内容' }));
    expect(writeText).toHaveBeenCalledWith(query.detailSql);
  });

  it('keeps resource details for Load without exposing an SQL drawer', () => {
    render(<TaskDetailDrawer task={load} open onClose={vi.fn()} />);

    const beRow = screen.getByText('be-1').closest('tr');
    expect(beRow).not.toBeNull();
    expect(within(beRow as HTMLElement).getByText('不适用')).toBeInTheDocument();
    expect(
      screen.queryByTestId('doris-active-task-sql'),
    ).not.toBeInTheDocument();
  });
});
