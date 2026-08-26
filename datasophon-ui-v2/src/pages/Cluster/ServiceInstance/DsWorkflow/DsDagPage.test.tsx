import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import DsDagPage from './DsDagPage';
import { getDsDag } from './service';

const { historyPush } = vi.hoisted(() => ({ historyPush: vi.fn() }));

vi.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
  useParams: () => ({
    instanceId: '33',
    projectCode: '1001',
    workflowInstanceId: '810001',
  }),
  history: { push: historyPush },
}));

vi.mock('./DsDagGraph', () => ({ default: () => <div>graph</div> }));
vi.mock('./service', () => ({ getDsDag: vi.fn() }));

const instance: DATASOPHON.DsWorkflowInstance = {
  id: 810001,
  workflowCode: 800001,
  name: 'synthetic instance',
  state: 'RUNNING_EXECUTION',
  durationSeconds: 10,
  dryRun: false,
};

const dag: DATASOPHON.DsDag = {
  instance,
  nodes: [],
  edges: [],
  locations: [],
};

function renderPage() {
  return render(
    <ClusterContext.Provider value={{ clusterId: 7 } as never}>
      <DsDagPage />
    </ClusterContext.Provider>,
  );
}

describe('DsDagPage polling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(getDsDag).mockResolvedValue({ success: true, data: dag });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('fetches the DAG for the route params and polls every 15 seconds until unmounted', async () => {
    const { unmount } = renderPage();
    await act(async () => Promise.resolve());
    expect(getDsDag).toHaveBeenCalledWith(7, 1001, 810001);
    expect(getDsDag).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(15_000);
      await Promise.resolve();
    });
    expect(getDsDag).toHaveBeenCalledTimes(2);

    unmount();
    await act(async () => {
      vi.advanceTimersByTime(30_000);
      await Promise.resolve();
    });
    expect(getDsDag).toHaveBeenCalledTimes(2);
  });

  it('navigates back to the service instance page', async () => {
    const { getByText } = renderPage();
    await act(async () => Promise.resolve());

    getByText('dsWorkflow.dag.backToList').click();
    expect(historyPush).toHaveBeenCalledWith('/cluster/7/service/33');
  });
});
