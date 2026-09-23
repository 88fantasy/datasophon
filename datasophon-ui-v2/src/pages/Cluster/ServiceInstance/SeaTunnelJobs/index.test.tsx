import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SeaTunnelJobs from './index';

const locale = vi.hoisted(() => ({ language: 'zh-CN' }));

vi.mock('@umijs/max', async () => {
  const zh = (await import('@/locales/zh-CN/seaTunnel')).default;
  const en = (await import('@/locales/en-US/seaTunnel')).default;
  const intl = {
    formatMessage: ({ id }: { id: string }, values?: Record<string, string>) =>
      ((locale.language === 'en-US' ? en : zh) as Record<string, string>)[
        id
      ]?.replace(/\{(\w+)\}/g, (_, key) => values?.[key] ?? '') ?? id,
  };
  return { useIntl: () => intl };
});

const {
  getSeaTunnelOverview,
  getSeaTunnelWorkers,
  getSeaTunnelPendingJobs,
  getSeaTunnelJobs,
  getSeaTunnelJobInfo,
} = vi.hoisted(() => ({
  getSeaTunnelOverview: vi.fn(),
  getSeaTunnelWorkers: vi.fn(),
  getSeaTunnelPendingJobs: vi.fn(),
  getSeaTunnelJobs: vi.fn(),
  getSeaTunnelJobInfo: vi.fn(),
}));

vi.mock('./service', () => ({
  getSeaTunnelOverview,
  getSeaTunnelWorkers,
  getSeaTunnelPendingJobs,
  getSeaTunnelJobs,
  getSeaTunnelJobInfo,
}));

const apiResponse = <T,>(data: T) => ({ success: true, data });

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

function renderVisibleTab() {
  return render(
    <div role="tabpanel" aria-hidden="false">
      <SeaTunnelJobs clusterId={7} instanceId={8} />
    </div>,
  );
}

describe('SeaTunnelJobs', () => {
  beforeEach(() => {
    locale.language = 'zh-CN';
    vi.clearAllMocks();
    getSeaTunnelOverview.mockResolvedValue(
      apiResponse({
        projectVersion: '3.0.0',
        gitCommitAbbrev: 'abc123',
        totalSlot: '8',
        runningJobs: '2',
        finishedJobs: '4',
        failedJobs: '1',
        pendingJobs: '0',
        cancelledJobs: '0',
        workers: '3',
      }),
    );
    getSeaTunnelWorkers.mockResolvedValue(
      apiResponse({
        workers: [
          {
            address: 'worker-1',
            totalSlots: '4',
            usedSlots: '2',
            runningJobIds: ['job-1'],
            totalHeapMemoryBytes: '1073741824',
          },
        ],
      }),
    );
    getSeaTunnelPendingJobs.mockResolvedValue(
      apiResponse({
        queueSummary: { size: '0' },
        pendingJobs: [],
      }),
    );
    getSeaTunnelJobs.mockResolvedValue(
      apiResponse([
        {
          jobId: 'job-1',
          jobName: 'daily-load',
          jobStatus: 'RUNNING',
          createTime: '2026-09-22',
          finishTime: '',
        },
      ]),
    );
    getSeaTunnelJobInfo.mockResolvedValue(
      apiResponse({
        jobId: 'job-1',
        jobName: 'daily-load',
        jobStatus: 'RUNNING',
        metrics: {
          readRows: '12',
          TableSourceReceivedCount: { 'db.t1': '15' },
        },
      }),
    );
  });

  it('shows overview in dynamic slot mode without a free-slot value', async () => {
    const { container } = renderVisibleTab();

    expect(await screen.findByText('3.0.0')).toBeInTheDocument();
    expect(screen.getByText('动态 slot 模式')).toBeInTheDocument();
    expect(container.textContent?.toLowerCase()).not.toMatch(/free|unassigned/);
  });

  it('renders English labels in en-US', async () => {
    locale.language = 'en-US';
    renderVisibleTab();

    expect(await screen.findByText('Dynamic slot mode')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Finished' })).toBeInTheDocument();
  });

  it('shows the all-masters-unavailable message verbatim', async () => {
    const message = 'SeaTunnel master 均不可达: master-a:18088, master-b:18088';
    getSeaTunnelOverview.mockRejectedValueOnce({
      message: 'Request failed with status code 502',
      response: {
        data: { success: false, errorCode: 502, errorMessage: message },
      },
    });

    renderVisibleTab();

    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it('does not request data while its tab panel is hidden', async () => {
    render(
      <div role="tabpanel" aria-hidden="true">
        <SeaTunnelJobs clusterId={7} instanceId={8} />
      </div>,
    );

    await waitFor(() => {
      expect(getSeaTunnelOverview).not.toHaveBeenCalled();
      expect(getSeaTunnelWorkers).not.toHaveBeenCalled();
      expect(getSeaTunnelPendingJobs).not.toHaveBeenCalled();
      expect(getSeaTunnelJobs).not.toHaveBeenCalled();
    });
  });

  it('starts requesting data when its tab becomes visible', async () => {
    const { container } = render(
      <div role="tabpanel" aria-hidden="true">
        <SeaTunnelJobs clusterId={7} instanceId={8} />
      </div>,
    );
    expect(getSeaTunnelOverview).not.toHaveBeenCalled();

    act(() => {
      container.firstElementChild?.setAttribute('aria-hidden', 'false');
    });

    await waitFor(() => expect(getSeaTunnelOverview).toHaveBeenCalledTimes(1));
  });

  it('loads the selected job into the details drawer', async () => {
    renderVisibleTab();

    fireEvent.click(await screen.findByRole('button', { name: '详情' }));

    expect(getSeaTunnelJobInfo).toHaveBeenCalledWith(7, 8, 'job-1');
    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('{"db.t1":"15"}')).toBeInTheDocument();
  });

  it('ignores an older job list response after switching states', async () => {
    const staleRunningResponse = deferred<{
      success: boolean;
      data: Array<{ jobId: string }>;
    }>();
    getSeaTunnelJobs.mockImplementation((_clusterId, _instanceId, state) =>
      state === 'running'
        ? staleRunningResponse.promise
        : Promise.resolve(apiResponse([{ jobId: 'finished-job' }])),
    );

    renderVisibleTab();
    await waitFor(() => expect(getSeaTunnelJobs).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('radio', { name: '已完成' }));
    expect(await screen.findByText('finished-job')).toBeInTheDocument();

    await act(async () => {
      staleRunningResponse.resolve(
        apiResponse([{ jobId: 'stale-running-job' }]),
      );
    });

    const jobTable = within(screen.getByTestId('seatunnel-jobs-table'));
    expect(jobTable.getAllByRole('row')[1]).toHaveTextContent('finished-job');
    expect(jobTable.queryByText('stale-running-job')).not.toBeInTheDocument();
  });

  it('keeps the most recently selected job details when requests finish out of order', async () => {
    const staleDetailsResponse = deferred<{
      success: boolean;
      data: { jobId: string; metrics: { readRows: string } };
    }>();
    getSeaTunnelJobs.mockResolvedValue(
      apiResponse([{ jobId: 'job-1' }, { jobId: 'job-2' }]),
    );
    getSeaTunnelJobInfo.mockImplementation((_clusterId, _instanceId, jobId) =>
      jobId === 'job-1'
        ? staleDetailsResponse.promise
        : Promise.resolve(
            apiResponse({
              jobId: 'job-2',
              metrics: { readRows: 'fresh-detail' },
            }),
          ),
    );

    renderVisibleTab();
    const jobTable = within(await screen.findByTestId('seatunnel-jobs-table'));
    const detailButtons = await jobTable.findAllByRole('button', {
      name: '详情',
    });
    fireEvent.click(detailButtons[0]);
    fireEvent.click(detailButtons[1]);

    expect(await screen.findByText('fresh-detail')).toBeInTheDocument();
    await act(async () => {
      staleDetailsResponse.resolve(
        apiResponse({ jobId: 'job-1', metrics: { readRows: 'stale-detail' } }),
      );
    });

    expect(screen.getByText('作业详情：job-2')).toBeInTheDocument();
    expect(screen.getByText('fresh-detail')).toBeInTheDocument();
    expect(screen.queryByText('stale-detail')).not.toBeInTheDocument();
  });

  it('lets the user expand the pending panel when no job is queued', async () => {
    renderVisibleTab();

    const header = await screen.findByRole('button', { name: /排队作业/ });
    expect(header).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(header);
    expect(header).toHaveAttribute('aria-expanded', 'true');
  });

  it('renders an en dash for fields missing from Zeta responses', async () => {
    getSeaTunnelOverview.mockResolvedValue(apiResponse({}));
    getSeaTunnelWorkers.mockResolvedValue(apiResponse({ workers: [{}] }));
    getSeaTunnelPendingJobs.mockResolvedValue(apiResponse({ pendingJobs: [] }));
    getSeaTunnelJobs.mockResolvedValue(apiResponse([{}]));

    renderVisibleTab();

    const workerTable = await screen.findByTestId('seatunnel-workers-table');
    const jobsTable = screen.getByTestId('seatunnel-jobs-table');
    await waitFor(() => {
      expect(within(workerTable).getAllByRole('row')[1]).toHaveTextContent('–');
      expect(within(jobsTable).getAllByRole('row')[1]).toHaveTextContent('–');
      expect(screen.getByTestId('seatunnel-total-slot')).toHaveTextContent('–');
    });
  });
});
