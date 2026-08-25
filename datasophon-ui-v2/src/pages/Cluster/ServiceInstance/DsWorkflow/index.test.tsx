import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDsProjects } from '@/services/dsWorkflow';
import DsWorkflowPanel from './index';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

vi.mock('antd', () => ({
  Alert: ({ title }: { title: ReactNode }) => <div>{title}</div>,
  Button: ({
    children,
    onClick,
  }: {
    children: ReactNode;
    onClick?: () => void;
  }) => (
    <button type="button" onClick={onClick}>
      {children}
    </button>
  ),
  Select: ({
    value,
    options,
    onChange,
    'aria-label': ariaLabel,
  }: {
    value?: number;
    options: Array<{ label: string; value: number }>;
    onChange?: (value: number) => void;
    'aria-label'?: string;
  }) => (
    <select
      aria-label={ariaLabel}
      value={value ?? ''}
      onChange={(event) => onChange?.(Number(event.target.value))}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Space: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  Typography: {
    Text: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  },
}));

vi.mock('@/services/dsWorkflow', () => ({ getDsProjects: vi.fn() }));

describe('DsWorkflowPanel project selector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getDsProjects).mockResolvedValue({
      success: true,
      data: {
        list: [
          { code: 1001, name: 'synthetic batch' },
          { code: 1002, name: 'synthetic stream' },
        ],
        total: 2,
        pageNo: 1,
        pageSize: 200,
      },
    });
  });

  it('loads projects and lets the user switch selection', async () => {
    render(<DsWorkflowPanel clusterId={7} instanceId={33} />);

    const selector = await screen.findByLabelText('dsWorkflow.project.label');
    await waitFor(() => expect(selector).toHaveValue('1001'));
    fireEvent.change(selector, { target: { value: '1002' } });

    expect(selector).toHaveValue('1002');
    expect(getDsProjects).toHaveBeenCalledWith(7);
    expect(screen.getByText('dsWorkflow.skeleton.ready')).toBeInTheDocument();
  });
});
