import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ActiveTaskTable from './ActiveTaskTable';
import type { DorisActiveTask } from './types';

const tasks: DorisActiveTask[] = [
  { taskId: 'q-1', type: 'QUERY', user: 'alice' },
  { taskId: 'load-1', type: 'LOAD' },
];

describe('ActiveTaskTable', () => {
  it('renders Query and Load rows and forwards row selection', () => {
    const onOpen = vi.fn();
    render(<ActiveTaskTable tasks={tasks} onOpen={onOpen} />);

    expect(screen.getByText('q-1')).toBeInTheDocument();
    expect(screen.getByText('load-1')).toBeInTheDocument();
    fireEvent.click(screen.getByText('q-1').closest('tr') as HTMLElement);
    expect(onOpen).toHaveBeenCalledWith(tasks[0]);
  });
});
