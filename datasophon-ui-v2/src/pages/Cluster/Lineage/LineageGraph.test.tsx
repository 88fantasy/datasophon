import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { Mock } from 'vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import LineageGraph from './LineageGraph';
import { getGraph, getImpact, getJob, getJobMetrics, listTables } from './service';
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
    data: {
      nodes?: Array<{ id: string; data?: Record<string, unknown> }>;
      edges?: Array<{
        id: string;
        source: string;
        target: string;
        data?: Record<string, unknown>;
      }>;
    };
    options: Record<string, unknown>;
    render: Mock;
    fitView: Mock;
    updateNodeData: (updates: Array<{ id: string; data?: Record<string, unknown> }>) => void;
    updateEdgeData: (updates: Array<{ id: string; data?: Record<string, unknown> }>) => void;
    draw: () => Promise<void>;
  }>,
}));

vi.mock('@antv/g6', () => ({
  CubicHorizontal: class {
    protected shapeMap = { key: { animate: vi.fn() } };
    destroy() {}
  },
  ExtensionCategory: { EDGE: 'edge' },
  Graph: class {
    handlers: Record<string, (event: unknown) => void> = {};
    data: {
      nodes?: Array<{ id: string; data?: Record<string, unknown> }>;
      edges?: Array<{
        id: string;
        source: string;
        target: string;
        data?: Record<string, unknown>;
      }>;
    };
    options: Record<string, unknown>;

    constructor(options: {
      data?: {
        nodes?: Array<{ id: string; data?: Record<string, unknown> }>;
        edges?: Array<{
          id: string;
          source: string;
          target: string;
          data?: Record<string, unknown>;
        }>;
      };
    }) {
      this.data = options.data ?? {};
      this.options = options as unknown as Record<string, unknown>;
      graphInstances.push(this);
    }

    on(name: string, handler: (event: unknown) => void) {
      this.handlers[name] = handler;
    }

    render = vi.fn();

    fitView = vi.fn();

    setData(data: {
      nodes?: Array<{ id: string; data?: Record<string, unknown> }>;
      edges?: Array<{
        id: string;
        source: string;
        target: string;
        data?: Record<string, unknown>;
      }>;
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

    // P1 增量刷新路径用到的 G6 v5 API：按 id 把 data 浅合并进已有节点/边（与真实 G6 的
    // mergeElementsData 语义一致），不整体替换、不触发 render/fitView。
    updateNodeData(updates: Array<{ id: string; data?: Record<string, unknown> }>) {
      updates.forEach((update) => {
        const node = this.data.nodes?.find((item) => item.id === update.id);
        if (node) node.data = { ...(node.data ?? {}), ...(update.data ?? {}) };
      });
    }

    updateEdgeData(updates: Array<{ id: string; data?: Record<string, unknown> }>) {
      updates.forEach((update) => {
        const edge = this.data.edges?.find((item) => item.id === update.id);
        if (edge) edge.data = { ...(edge.data ?? {}), ...(update.data ?? {}) };
      });
    }

    draw() {
      return Promise.resolve();
    }

    destroy() {}
  },
  register: vi.fn(),
}));

vi.mock('./service', () => ({
  getGraph: vi.fn(),
  getImpact: vi.fn(),
  getJob: vi.fn(),
  getJobMetrics: vi.fn(),
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
    vi.mocked(getJobMetrics).mockReset();
    vi.mocked(getJobMetrics).mockResolvedValue({});
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
        depth: 3,
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
        { clusterId: 7, rootNodeId: 1, depth: 3 },
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
      depth: 3,
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

  it('opens the job detail drawer with a per-target-table breakdown for a multi-output job, matching the value shown via its own edge', async () => {
    vi.mocked(getGraph).mockResolvedValue({
      data: {
        nodes: [node(1, 'a'), node(2, 'b'), node(3, 'c'), node(4, 'd')],
        edges: [
          { src: 1, dst: 2, jobs: [job({ edgeId: 100, lastRowCount: 10, lastBytes: 10 })] },
          { src: 1, dst: 3, jobs: [job({ edgeId: 101, lastRowCount: 20, lastBytes: 20 })] },
          { src: 1, dst: 4, jobs: [job({ edgeId: 102, lastRowCount: 30, lastBytes: 30 })] },
        ],
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

    // P6：点任务节点应按目标表分行显示各自的真实统计，而不是像修复前那样把节点自身的
    // 统计置空、全部显示 "-"（那是因为一个节点只能持有一份统计，而后端算的是按 dst 的）。
    graphInstances[0].handlers['node:click']({ target: { id: 'job:10' } });
    expect(await screen.findByText('关联作业')).toBeInTheDocument();
    const table = screen.getByText('最近运行统计').closest('.ant-table');
    expect(table).not.toBeNull();
    const withinTable = within(table as HTMLElement);
    expect(withinTable.getByText('b')).toBeInTheDocument();
    expect(withinTable.getByText('c')).toBeInTheDocument();
    expect(withinTable.getByText('d')).toBeInTheDocument();
    expect(withinTable.getByText('10行')).toBeInTheDocument();
    expect(withinTable.getByText('10 B')).toBeInTheDocument();
    expect(withinTable.getByText('20行')).toBeInTheDocument();
    expect(withinTable.getByText('20 B')).toBeInTheDocument();
    expect(withinTable.getByText('30行')).toBeInTheDocument();
    expect(withinTable.getByText('30 B')).toBeInTheDocument();
    await waitFor(() =>
      expect(getJob).toHaveBeenCalledWith(7, 10, { skipErrorHandler: true }),
    );

    // 同一个作业，改点它写往 "c" 表的那条出边：应看到与上面表格里 "c" 那一行完全一致的
    // 20行/20 B，而不是修复前那种"节点显示 - 、边显示真值"的自相矛盾。
    graphInstances[0].handlers['edge:click']({ target: { id: 'job:10->3' } });
    // "20行"/"20 B" 在切换前就已经作为表格里 c 那一行的文本存在——不能用 findByText 简单查一次
    // 就断言通过，那样会在 React 还没提交这次点击触发的重渲染前，误命中切换前遗留的旧节点。
    // 用 waitFor 连着 closest 断言一起重试，直到真正落到新渲染出的 Descriptions 结构上。
    await waitFor(() => {
      expect(screen.getByText('20行').closest('.ant-descriptions')).not.toBeNull();
      expect(screen.getByText('20 B').closest('.ant-descriptions')).not.toBeNull();
    });
  });

  it('does not poll job metrics when the graph has no running jobs', async () => {
    const setIntervalSpy = vi.spyOn(window, 'setInterval');
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

    renderGraphPage();
    await waitFor(() => expect(getGraph).toHaveBeenCalledTimes(1));

    expect(getJobMetrics).not.toHaveBeenCalled();
    expect(
      setIntervalSpy.mock.calls.some(([, delay]) => delay === 15_000),
    ).toBe(false);
    setIntervalSpy.mockRestore();
  });

  it('polls running job metrics, renders progress/tooltip/flowing edges, and clears the timer on unmount', async () => {
    const setIntervalSpy = vi.spyOn(window, 'setInterval');
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    vi.mocked(getGraph).mockResolvedValue({
      data: {
        nodes: [node(1, 'a'), node(2, 'b')],
        edges: [
          {
            src: 1,
            dst: 2,
            jobs: [job({ runningAppId: 'application_1' })],
          },
        ],
        collapsed: [],
        truncated: false,
      },
      snapshot: FRESH_SNAPSHOT,
      sourceFreshness: OK_SOURCE,
    });
    vi.mocked(getJobMetrics).mockResolvedValue({
      application_1: {
        completeTasks: 12,
        activeTasks: 2,
        recordsWritten: 60_000_000,
        bytesWritten: 2_204_955_464,
        recordsWrittenRate: 51_234.5,
        runningStages: 1,
        sampledAt: '2026-08-04T03:01:44Z',
      },
    });

    const view = renderGraphPage();

    await waitFor(() =>
      expect(getJobMetrics).toHaveBeenCalledWith(7, ['application_1']),
    );
    const graph = graphInstances[0];
    await waitFor(() =>
      expect(
        graph.data.nodes?.find((item) => item.id === 'job:10')?.data
          ?.runtimeLabel,
      ).toBe('14 task · 5.1万行/秒'),
    );

    // P1 回归：runtimeLabel 的这次变化来自指标轮询（初次 refresh()，与之后每 15 秒的
    // 轮询走同一条增量更新 effect），不是走 setData/render 重新建图那条路径，
    // 所以 fitView 不应该被再次调用——否则用户手动缩放/拖动过的视角会被拉回默认状态。
    expect(graph.render).toHaveBeenCalledTimes(1);
    expect(graph.fitView).toHaveBeenCalledTimes(1);

    const intervalIndex = setIntervalSpy.mock.calls.findIndex(
      ([, delay]) => delay === 15_000,
    );
    expect(intervalIndex).toBeGreaterThanOrEqual(0);
    const intervalId = setIntervalSpy.mock.results[intervalIndex].value;

    const options = graph.options as {
      node: { style: { labelText: (datum: unknown) => string } };
      edge: { type: (datum: unknown) => string };
      plugins: Array<{
        type: string;
        getContent?: (event: unknown, items: unknown[]) => Promise<HTMLElement | string>;
      }>;
    };
    const jobNode = graph.data.nodes?.find((item) => item.id === 'job:10');
    expect(options.node.style.labelText(jobNode)).toBe(
      'sync_orders\n14 task · 5.1万行/秒',
    );
    const runningEdges = graph.data.edges?.filter(
      (edge) => edge.source === 'job:10' || edge.target === 'job:10',
    );
    expect(runningEdges).toHaveLength(2);
    expect(
      runningEdges?.every(
        (edge) => options.edge.type(edge) === 'lineage-flowing-edge',
      ),
    ).toBe(true);

    const tooltip = options.plugins.find((plugin) => plugin.type === 'tooltip');
    const tooltipContent = await tooltip?.getContent?.({}, [jobNode]);
    expect((tooltipContent as HTMLElement).textContent).toContain('14 task · 5.1万行/秒');

    const tableNode = graph.data.nodes?.find((item) => item.id === '1');
    const tableTooltip = await tooltip?.getContent?.({}, [tableNode]);
    expect((tableTooltip as HTMLElement).textContent).toContain('表名：a');
    expect((tableTooltip as HTMLElement).textContent).toContain('完整名称：a');
    expect((tableTooltip as HTMLElement).style.maxWidth).toBe('min(520px, calc(100vw - 48px))');
    expect((tableTooltip as HTMLElement).style.overflowWrap).toBe('anywhere');

    view.unmount();
    expect(clearIntervalSpy).toHaveBeenCalledWith(intervalId);
    setIntervalSpy.mockRestore();
    clearIntervalSpy.mockRestore();
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
