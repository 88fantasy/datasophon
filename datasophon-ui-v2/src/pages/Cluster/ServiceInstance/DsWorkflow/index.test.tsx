import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getDsProjects,
  getDsWorkflowInstances,
  getDsWorkflows,
} from '@/services/dsWorkflow';
import DsWorkflowPanel from './index';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

vi.mock('@ant-design/pro-components', () => ({
  ProTable: (props: {
    request: (params: {
      current: number;
      pageSize: number;
    }) => Promise<{ data?: Array<Record<string, unknown>> }>;
    expandable?: {
      expandedRowRender: (record: Record<string, unknown>) => ReactNode;
    };
    onRow?: (record: Record<string, unknown>) => { onClick?: () => void };
  }) => {
    const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
    const [expanded, setExpanded] = useState<Record<string, unknown>>();
    useEffect(() => {
      void props
        .request({ current: 1, pageSize: 20 })
        .then((result) => setRows(result.data ?? []));
    }, [props]);
    return (
      <div>
        {rows.map((row) => {
          const key = String(row.code ?? row.id);
          return (
            <div key={key}>
              <button
                type="button"
                data-testid={`row-${key}`}
                onClick={() => props.onRow?.(row).onClick?.()}
              >
                {String(row.name)}
              </button>
              {props.expandable ? (
                <button type="button" onClick={() => setExpanded(row)}>
                  expand-{key}
                </button>
              ) : null}
            </div>
          );
        })}
        {expanded && props.expandable
          ? props.expandable.expandedRowRender(expanded)
          : null}
      </div>
    );
  },
}));

vi.mock('antd', () => ({
  Alert: ({ title }: { title: ReactNode }) => <div>{title}</div>,
  Button: ({ children }: { children: ReactNode }) => (
    <button type="button">{children}</button>
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
  Tag: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  Typography: {
    Text: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  },
}));

vi.mock('./DsDagDrawer', () => ({
  default: ({
    open,
    instance,
  }: {
    open: boolean;
    instance?: { name: string };
  }) => (
    <div data-testid="dag-drawer" data-open={open}>
      {instance?.name}
    </div>
  ),
}));

vi.mock('@/services/dsWorkflow', () => ({
  getDsProjects: vi.fn(),
  getDsWorkflows: vi.fn(),
  getDsWorkflowInstances: vi.fn(),
}));

describe('DsWorkflowPanel tree table', () => {
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
    vi.mocked(getDsWorkflows).mockResolvedValue({
      success: true,
      data: {
        list: [
          {
            code: 800001,
            name: 'synthetic workflow',
            version: 1,
            releaseState: 'ONLINE',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      },
    });
    vi.mocked(getDsWorkflowInstances).mockResolvedValue({
      success: true,
      data: {
        list: [
          {
            id: 810001,
            workflowCode: 800001,
            name: 'synthetic instance',
            state: 'SUCCESS',
            durationSeconds: 16,
            dryRun: false,
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 10,
      },
    });
  });

  it('loads definitions, lazily loads instances, and only opens an instance row', async () => {
    render(<DsWorkflowPanel clusterId={7} instanceId={33} />);

    await screen.findByText('synthetic workflow');
    expect(getDsWorkflowInstances).not.toHaveBeenCalled();
    expect(screen.getByTestId('dag-drawer')).toHaveAttribute(
      'data-open',
      'false',
    );

    fireEvent.click(screen.getByText('expand-800001'));
    await screen.findByText('synthetic instance');
    expect(getDsWorkflowInstances).toHaveBeenCalledWith(7, 1001, 800001);
    expect(screen.getByTestId('dag-drawer')).toHaveAttribute(
      'data-open',
      'false',
    );

    fireEvent.click(screen.getByTestId('row-810001'));
    expect(screen.getByTestId('dag-drawer')).toHaveAttribute(
      'data-open',
      'true',
    );
  });

  it('loads projects and refreshes definitions when project selection changes', async () => {
    render(<DsWorkflowPanel clusterId={7} instanceId={33} />);

    const selector = await screen.findByLabelText('dsWorkflow.project.label');
    await waitFor(() => expect(selector).toHaveValue('1001'));
    fireEvent.change(selector, { target: { value: '1002' } });

    expect(selector).toHaveValue('1002');
    await waitFor(() =>
      expect(getDsWorkflows).toHaveBeenCalledWith(7, 1002, 1, 20, undefined),
    );
  });

  it.each([
    [
      {
        info: {
          errorCode: 400,
          errorMessage: '请在 DS 服务配置中填写 apiToken',
        },
      },
      'dsWorkflow.error.tokenMissing',
    ],
    [
      { response: { status: 401, data: { errorMessage: 'DS apiToken 已失效' } } },
      'dsWorkflow.error.tokenInvalid',
    ],
    [
      {
        response: {
          status: 502,
          data: { errorMessage: 'DS Open API 不可达或请求超时' },
        },
      },
      'dsWorkflow.error.unavailable',
    ],
  ])('shows a distinct inline error for %j', async (requestError, expected) => {
    vi.mocked(getDsProjects).mockRejectedValueOnce(requestError);

    render(<DsWorkflowPanel clusterId={7} instanceId={33} />);

    expect(await screen.findByText(expected)).toBeInTheDocument();
  });
});
