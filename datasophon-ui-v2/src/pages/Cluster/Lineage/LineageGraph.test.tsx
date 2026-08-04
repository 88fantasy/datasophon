import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import LineageGraph from './LineageGraph';
import { getGraph, getImpact, getJob, listTables } from './service';
import type { GraphJob } from './service';

const { historyPush } = vi.hoisted(() => ({ historyPush: vi.fn() }));

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
  useParams: () => ({ nodeId: '1' }),
  history: { push: historyPush },
}));

const { graphInstances } = vi.hoisted(() => ({
  graphInstances: [] as Array<{
    handlers: Record<string, (event: unknown) => void>;
    data: { nodes?: Array<{ id: string; data?: Record<string, unknown> }> };
  }>,
}));

vi.mock('@antv/g6', () => ({
  Graph: class {
    handlers: Record<string, (event: unknown) => void> = {};
    data: { nodes?: Array<{ id: string; data?: Record<string, unknown> }> };

    constructor(options: {
      data?: { nodes?: Array<{ id: string; data?: Record<string, unknown> }> };
    }) {
      this.data = options.data ?? {};
      graphInstances.push(this);
    }

    on(name: string, handler: (event: unknown) => void) {
      this.handlers[name] = handler;
    }

    render() {}

    fitView() {}

    setData(data: {
      nodes?: Array<{ id: string; data?: Record<string, unknown> }>;
    }) {
      this.data = data;
    }

    getNodeData(id: string) {
      return this.data.nodes?.find((node) => node.id === id);
    }

    getEdgeData(id: string) {
      const edges = (
        this as unknown as {
          data: { edges?: Array<{ id: string; data?: Record<string, unknown> }> };
        }
      ).data.edges;
      return edges?.find((edge) => edge.id === id);
    }

    destroy() {}
  },
}));

vi.mock('./service', () => ({
  getGraph: vi.fn(),
  getImpact: vi.fn(),
  getJob: vi.fn(),
  listTables: vi.fn(),
}));

const FRESH_SNAPSHOT = {
  generation: 3,
  targetGeneration: 3,
  builtAt: new Date().toISOString(),
  ageSeconds: 5,
  stale: false,
  lastRebuildError: null,
};
const OK_SOURCE = { lastEventReceivedAt: null, status: 'OK' as const };

function node(id: number, canonicalName: string) {
  return {
    id,
    clusterId: 7,
    connector: 'hive',
    catalogName: 'c',
    databaseName: 'ods',
    tableName: canonicalName,
    canonicalName,
    dwLayer: null,
  };
}

function job(overrides: Partial<GraphJob> = {}): GraphJob {
  return {
    jobId: 10,
    edgeId: 100,
    flowType: 'OUTPUT',
    jobName: 'sync_orders',
    lastRowCount: 1_200_000,
    lastBytes: 64 * 1024 * 1024,
    lastRunAt: '2026-08-04T03:00:00Z',
    runningAppId: null,
    ...overrides,
  };
}

function renderGraphPage() {
  return render(
    <ClusterContext.Provider
      value={{ clusterId: 7, clusterInfo: {} } as never}
    >
      <LineageGraph />
    </ClusterContext.Provider>,
  );
}

describe('LineageGraph', () => {
  beforeEach(() => {
    graphInstances.length = 0;
    historyPush.mockReset();
    vi.mocked(getGraph).mockReset();
    vi.mocked(getImpact).mockReset();
    vi.mocked(getJob).mockReset();
    vi.mocked(listTables).mockReset();
  });

  it('loads the root graph with default depth/direction and renders freshness', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: { nodes: [node(1, 'a'), node(2, 'b')], edges: [{ src: 1, dst: 2, jobs: [] }], collapsed: [], truncated: false },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });

    renderGraphPage();

    await waitFor(() =>
      expect(getGraph).toHaveBeenCalledWith({
        clusterId: 7,
        rootNodeId: 1,
        depth: 2,
        direction: 'both',
      }),
    );
    expect(await screen.findByText(/快照构建于/)).toBeInTheDocument();
  });

  it('switches to impact analysis and calls getImpact instead of getGraph', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: { nodes: [node(1, 'a')], edges: [], collapsed: [], truncated: false },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getImpact).mockResolvedValue({
      data: { nodes: [node(1, 'a'), node(2, 'b')], edges: [], collapsed: [], truncated: false },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });

    renderGraphPage();
    await waitFor(() => expect(getGraph).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('switch'));

    await waitFor(() =>
      expect(getImpact).toHaveBeenCalledWith(
        { clusterId: 7, rootNodeId: 1, depth: 2 },
        { skipErrorHandler: true },
      ),
    );
  });

  it('shows an inline alert when impact analysis is unavailable (503, stale snapshot)', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: { nodes: [node(1, 'a')], edges: [], collapsed: [], truncated: false },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getImpact).mockRejectedValue({ response: { status: 503, data: {} } });

    renderGraphPage();
    await waitFor(() => expect(getGraph).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('switch'));

    expect(
      await screen.findByText(/快照陈旧，影响分析暂不可用/),
    ).toBeInTheDocument();
  });

  it('expands a collapsed node via the expand token and merges the result', async () => {
    vi.mocked(getGraph).mockResolvedValueOnce({
      data: {
        nodes: [node(1, 'a')],
        edges: [],
        collapsed: [
          { type: 'collapsed', nodeId: 1, token: 'n:1:down:g3', hiddenCount: 2, direction: 'downstream' },
        ],
        truncated: true,
      },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getGraph).mockResolvedValueOnce({
      data: {
        nodes: [node(1, 'a'), node(2, 'b')],
        edges: [{ src: 1, dst: 2, jobs: [] }],
        collapsed: [],
        truncated: false,
      },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });

    renderGraphPage();
    await waitFor(() => expect(graphInstances).toHaveLength(1));

    const graph = graphInstances[0];
    graph.handlers['node:click']({ target: { id: 'collapsed:n:1:down:g3' } });

    await waitFor(() =>
      expect(getGraph).toHaveBeenLastCalledWith(
        { clusterId: 7, rootNodeId: 1, expand: 'n:1:down:g3' },
        { skipErrorHandler: true },
      ),
    );
  });

  it('recovers from a stale expand token (409) by re-fetching the root query', async () => {
    vi.mocked(getGraph).mockResolvedValueOnce({
      data: {
        nodes: [node(1, 'a')],
        edges: [],
        collapsed: [
          { type: 'collapsed', nodeId: 1, token: 'n:1:down:g3', hiddenCount: 2, direction: 'downstream' },
        ],
        truncated: true,
      },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getGraph).mockRejectedValueOnce({ response: { status: 409, data: {} } });
    vi.mocked(getGraph).mockResolvedValueOnce({
      data: { nodes: [node(1, 'a')], edges: [], collapsed: [], truncated: false },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });

    renderGraphPage();
    await waitFor(() => expect(graphInstances).toHaveLength(1));

    const graph = graphInstances[0];
    graph.handlers['node:click']({ target: { id: 'collapsed:n:1:down:g3' } });

    await waitFor(() => expect(getGraph).toHaveBeenCalledTimes(3));
    expect(getGraph).toHaveBeenLastCalledWith({
      clusterId: 7,
      rootNodeId: 1,
      depth: 2,
      direction: 'both',
    });
  });

  it('opens the job detail drawer when a real (non-collapsed) edge is clicked', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: {
        nodes: [node(1, 'a'), node(2, 'b')],
        edges: [{ src: 1, dst: 2, jobs: [job({ flowType: 'INPUT' })] }],
        collapsed: [],
        truncated: false,
      },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getJob).mockResolvedValue({
      id: 10,
      clusterId: 7,
      jobName: 'sync',
      engine: 'spark',
      jobType: 'BATCH',
      dwLayer: null,
      owner: null,
      externalUrl: null,
      state: 'RUNNING',
      updateTime: '2026-08-01T00:00:00Z',
    });

    renderGraphPage();
    await waitFor(() => expect(graphInstances).toHaveLength(1));

    const graph = graphInstances[0];
    graph.handlers['edge:click']({ target: { id: '1->job:10' } });

    expect(await screen.findByText('关联作业')).toBeInTheDocument();
    await waitFor(() => expect(getJob).toHaveBeenCalledWith(7, 10, { skipErrorHandler: true }));
  });

  it('opens the job detail drawer when a job node is clicked', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: {
        nodes: [node(1, 'a'), node(2, 'b')],
        edges: [{ src: 1, dst: 2, jobs: [job()] }],
        collapsed: [],
        truncated: false,
      },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getJob).mockResolvedValue({
      id: 10,
      clusterId: 7,
      jobName: 'sync_orders',
      engine: 'spark',
      jobType: 'BATCH',
      dwLayer: null,
      owner: null,
      externalUrl: null,
      state: 'COMPLETE',
      updateTime: '2026-08-04T03:00:00Z',
    });

    renderGraphPage();
    await waitFor(() => expect(graphInstances).toHaveLength(1));

    graphInstances[0].handlers['node:click']({ target: { id: 'job:10' } });

    expect(await screen.findByText('关联作业')).toBeInTheDocument();
    await waitFor(() =>
      expect(getJob).toHaveBeenCalledWith(7, 10, {
        skipErrorHandler: true,
      }),
    );
  });

  it('searches by keyword and navigates to the matched table', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: { nodes: [node(1, 'a')], edges: [], collapsed: [], truncated: false },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(listTables).mockResolvedValue({
      data: { list: [node(9, 'orders')], total: 1 },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });

    renderGraphPage();
    await waitFor(() => expect(getGraph).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByPlaceholderText('搜索表名切换根节点'), {
      target: { value: 'orders' },
    });
    fireEvent.keyDown(screen.getByPlaceholderText('搜索表名切换根节点'), {
      key: 'Enter',
      code: 'Enter',
    });

    await waitFor(() =>
      expect(listTables).toHaveBeenCalledWith({ clusterId: 7, keyword: 'orders', size: 1 }),
    );
    await waitFor(() =>
      expect(historyPush).toHaveBeenCalledWith('/cluster/7/lineage/9'),
    );
  });
});
