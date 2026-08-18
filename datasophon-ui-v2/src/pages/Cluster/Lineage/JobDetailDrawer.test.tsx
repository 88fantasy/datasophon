import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import JobDetailDrawer from './JobDetailDrawer';
import type { GraphJob } from './service';
import { getJob, getJobRateHistory } from './service';

const { lineProps } = vi.hoisted(() => ({
  lineProps: [] as Array<Record<string, unknown>>,
}));

vi.mock('@ant-design/plots', () => ({
  Line: (props: Record<string, unknown>) => {
    lineProps.push(props);
    return <div data-testid="job-rate-chart" />;
  },
}));

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({
      id,
      defaultMessage,
    }: {
      id: string;
      defaultMessage?: string;
    }) => defaultMessage ?? id,
  }),
}));

vi.mock('./service', () => ({
  getJob: vi.fn(),
  getJobRateHistory: vi.fn(),
}));

function job(overrides: Partial<GraphJob> = {}): GraphJob {
  return {
    jobId: 10,
    edgeId: 100,
    flowType: 'INPUT',
    jobName: 'sync_orders',
    lastRowCount: 1_200_000,
    lastBytes: 64 * 1024 * 1024,
    lastRunAt: '2026-08-04T03:00:00Z',
    runningAppId: null,
    ...overrides,
  };
}

describe('JobDetailDrawer', () => {
  beforeEach(() => {
    lineProps.length = 0;
    vi.mocked(getJob).mockReset();
    vi.mocked(getJobRateHistory).mockReset();
    vi.mocked(getJobRateHistory).mockResolvedValue([]);
  });

  it('shows an empty state when the edge has no associated jobs', () => {
    render(<JobDetailDrawer clusterId={7} jobs={[]} open onClose={() => {}} />);
    expect(screen.getByText('此边未关联任何作业')).toBeInTheDocument();
  });

  it('fetches details for each unique job id and renders them', async () => {
    vi.mocked(getJob).mockResolvedValue({
      id: 10,
      clusterId: 7,
      jobName: 'sync_orders',
      engine: 'spark',
      jobType: 'BATCH',
      dwLayer: 'ODS',
      owner: 'alice',
      externalUrl: 'https://example.com/job/10',
      state: 'RUNNING',
      updateTime: '2026-08-01T00:00:00Z',
    });

    render(
      <JobDetailDrawer clusterId={7} jobs={[job()]} open onClose={() => {}} />,
    );

    expect(await screen.findByText('sync_orders')).toBeInTheDocument();
    expect(getJob).toHaveBeenCalledWith(7, 10, { skipErrorHandler: true });
    expect(screen.getByText('INPUT')).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      'https://example.com/job/10',
    );
    expect(screen.getByText('120万行')).toBeInTheDocument();
    expect(screen.getByText('64 MB')).toBeInTheDocument();
    expect(screen.getByText(/2026-08-04/)).toBeInTheDocument();
  });

  it('renders a per-target-table breakdown when the job node carries multiple outputs', async () => {
    // P6：job:xxx 节点点击带来的是 outputs（每个目标表一份统计），此时不能再用单值
    // Descriptions——节点本身的 lastRowCount 等字段已被 lineageGraphData.ts 置空。
    vi.mocked(getJob).mockResolvedValue({
      id: 10,
      clusterId: 7,
      jobName: 'sync_orders',
      engine: 'spark',
      jobType: 'BATCH',
      dwLayer: null,
      owner: null,
      externalUrl: null,
      state: 'RUNNING',
      updateTime: '2026-08-01T00:00:00Z',
    });

    render(
      <JobDetailDrawer
        clusterId={7}
        jobs={[
          {
            ...job(),
            lastRowCount: null,
            lastBytes: null,
            lastRunAt: null,
            outputs: [
              {
                dstNodeId: 2,
                dstName: 'output_a',
                lastRowCount: 10,
                lastBytes: 100,
                lastRunAt: '2026-08-04T03:00:00Z',
              },
              {
                dstNodeId: 3,
                dstName: 'output_b',
                lastRowCount: 20,
                lastBytes: 200,
                lastRunAt: '2026-08-04T03:00:00Z',
              },
            ],
          },
        ]}
        open
        onClose={() => {}}
      />,
    );

    expect(await screen.findByText('output_a')).toBeInTheDocument();
    expect(screen.getByText('output_b')).toBeInTheDocument();
    expect(screen.getByText('10行')).toBeInTheDocument();
    expect(screen.getByText('100 B')).toBeInTheDocument();
    expect(screen.getByText('20行')).toBeInTheDocument();
    expect(screen.getByText('200 B')).toBeInTheDocument();
    // 多输出场景走 Table 而不是 Descriptions，标题只应出现这一次。
    expect(screen.queryAllByText('最近运行统计')).toHaveLength(1);
  });

  it('deduplicates repeated jobIds across multiple job refs on the same edge', async () => {
    vi.mocked(getJob).mockResolvedValue({
      id: 10,
      clusterId: 7,
      jobName: 'sync_orders',
      engine: 'spark',
      jobType: 'BATCH',
      dwLayer: null,
      owner: null,
      externalUrl: null,
      state: 'RUNNING',
      updateTime: '2026-08-01T00:00:00Z',
    });

    render(
      <JobDetailDrawer
        clusterId={7}
        jobs={[job(), job({ edgeId: 101, flowType: 'OUTPUT' })]}
        open
        onClose={() => {}}
      />,
    );

    await waitFor(() => expect(getJob).toHaveBeenCalledTimes(1));
  });

  it('degrades gracefully when a job detail request fails', async () => {
    vi.mocked(getJob).mockRejectedValue(new Error('boom'));

    render(
      <JobDetailDrawer clusterId={7} jobs={[job()]} open onClose={() => {}} />,
    );

    expect(await screen.findByText('作业详情加载失败')).toBeInTheDocument();
  });

  it('loads and renders recordsWrittenRate history for a running job', async () => {
    vi.mocked(getJob).mockResolvedValue({
      id: 10,
      clusterId: 7,
      jobName: 'sync_orders',
      engine: 'spark',
      jobType: 'BATCH',
      dwLayer: null,
      owner: null,
      externalUrl: null,
      state: 'RUNNING',
      updateTime: '2026-08-04T03:00:00Z',
    });
    vi.mocked(getJobRateHistory).mockResolvedValue([
      { time: 1_800_000_000_000, value: 128.5 },
      { time: 1_800_000_060_000, value: 256.5 },
    ]);

    render(
      <JobDetailDrawer
        clusterId={7}
        jobs={[job({ runningAppId: 'application_1' })]}
        open
        onClose={() => {}}
      />,
    );

    expect(await screen.findByTestId('job-rate-chart')).toBeInTheDocument();
    expect(getJobRateHistory).toHaveBeenCalledWith(7, 'application_1');
    expect(lineProps.at(-1)?.data).toEqual([
      { time: 1_800_000_000_000, value: 128.5 },
      { time: 1_800_000_060_000, value: 256.5 },
    ]);
    const tooltip = lineProps.at(-1)?.tooltip as {
      items: Array<{ valueFormatter: (value: number) => string }>;
    };
    expect(tooltip.items[0].valueFormatter(3.456)).toBe('3.46');
  });
});
