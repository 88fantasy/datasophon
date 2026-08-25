import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DsWorkflowPanel from './index';
import {
  getDsProjects,
  getDsWorkflowInstances,
  getDsWorkflows,
} from './service';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

vi.mock('@ant-design/pro-components', () => ({
  ProTable: (props: {
    columns?: Array<{
      dataIndex?: string;
      render?: (value: unknown, record: Record<string, unknown>) => ReactNode;
    }>;
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
              {props.columns?.map((column, index) => (
                <div key={`${key}-${column.dataIndex ?? index}`}>
                  {column.render ? column.render(undefined, row) : null}
                </div>
              ))}
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
  Button: ({
    children,
    href,
    target,
  }: {
    children: ReactNode;
    href?: string;
    target?: string;
  }) =>
    href ? (
      <a href={href} target={target}>
        {children}
      </a>
    ) : (
      <button type="button">{children}</button>
    ),
  Select: ({
    value,
    options,
    onChange,
    'aria-label': ariaLabel,
  }: {
    value?: number | string;
    options: Array<{ label: string; value: number | string }>;
    onChange?: (value: number | string) => void;
    'aria-label'?: string;
  }) => (
    <select
      aria-label={ariaLabel}
      value={value ?? ''}
      onChange={(event) => {
        const option = options.find(
          (item) => String(item.value) === event.target.value,
        );
        if (option) onChange?.(option.value);
      }}
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

vi.mock('./service', () => ({
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

  it('filters the current workflow page by release state', async () => {
    vi.mocked(getDsWorkflows).mockResolvedValue({
      success: true,
      data: {
        list: [
          {
            code: 800001,
            name: 'online workflow',
            version: 1,
            releaseState: 'ONLINE',
          },
          {
            code: 800002,
            name: 'offline workflow',
            version: 1,
            releaseState: 'OFFLINE',
          },
        ],
        total: 2,
        pageNo: 1,
        pageSize: 20,
      },
    });
    render(<DsWorkflowPanel clusterId={7} instanceId={33} />);
    await screen.findByTestId('row-800001');

    fireEvent.change(
      screen.getByLabelText('dsWorkflow.filter.releaseState'),
      { target: { value: 'OFFLINE' } },
    );

    await screen.findByText('offline workflow');
    await waitFor(() =>
      expect(screen.queryByText('online workflow')).not.toBeInTheDocument(),
    );
  });

  it('offers a link to the DS native Web UI', async () => {
    render(
      <DsWorkflowPanel
        clusterId={7}
        instanceId={33}
        dsWebUrl="http://ds.example/dolphinscheduler/ui"
      />,
    );

    const link = await screen.findByRole('link', {
      name: 'dsWorkflow.action.openDs',
    });
    expect(link).toHaveAttribute(
      'href',
      'http://ds.example/dolphinscheduler/ui',
    );
    expect(link).toHaveAttribute('target', '_blank');
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
