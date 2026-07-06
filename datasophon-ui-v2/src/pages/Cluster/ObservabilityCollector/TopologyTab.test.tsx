import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getServiceSummary, getTraceTopology } from './service';
import TopologyTab, { toGraphData } from './TopologyTab';

const { graphInstances } = vi.hoisted(() => ({
  graphInstances: [] as Array<{
    handlers: Record<string, (event: unknown) => void>;
  }>,
}));

vi.mock('@antv/g6', () => ({
  CubicHorizontal: class {},
  ExtensionCategory: { EDGE: 'edge' },
  register: () => {},
  subStyleProps: () => ({}),
  Graph: class {
    handlers: Record<string, (event: unknown) => void> = {};

    constructor(_options: unknown) {
      graphInstances.push(this);
    }

    on(name: string, handler: (event: unknown) => void) {
      this.handlers[name] = handler;
    }

    render() {}

    setData() {}

    destroy() {}
  },
}));

vi.mock('./service', () => ({
  getTraceTopology: vi.fn(),
  getServiceSummary: vi.fn(),
  getTraceDetail: vi.fn(),
  listLogs: vi.fn(),
}));

describe('toGraphData', () => {
  it('maps services to nodes with latency and error metrics', () => {
    const { nodes } = toGraphData({
      nodes: [
        {
          serviceName: 'datasophon-api',
          spanCount: 100,
          errorCount: 5,
          avgDurationNs: 1_500_000,
          p99DurationNs: 9_000_000,
          maxDurationNs: 20_000_000,
        },
      ],
      edges: [],
    });

    expect(nodes).toHaveLength(1);
    expect(nodes[0].id).toBe('datasophon-api');
    expect(nodes[0].data.errorRate).toBeCloseTo(0.05);
    expect(nodes[0].data.metricsText).toContain('p99 9.00 ms');
    expect(nodes[0].data.metricsText).toContain('avg 1.50 ms');
    expect(nodes[0].data.metricsText).toContain('err 5.0%');
  });

  it('maps calls to directed edges and marks errors on the label', () => {
    const { edges } = toGraphData({
      nodes: [],
      edges: [
        {
          caller: 'datasophon-api',
          callee: 'datasophon-worker',
          callCount: 40,
          errorCount: 2,
        },
        {
          caller: 'datasophon-worker',
          callee: 'doris-fe',
          callCount: 10,
          errorCount: 0,
        },
      ],
    });

    expect(edges[0]).toMatchObject({
      source: 'datasophon-api',
      target: 'datasophon-worker',
    });
    expect(edges[0].data.labelText).toBe('40 · 5.0% err');
    expect(edges[1].data.labelText).toBe('10');
  });

  it('does not divide by zero for services without spans', () => {
    const { nodes } = toGraphData({
      nodes: [
        {
          serviceName: 'idle-service',
          spanCount: 0,
          errorCount: 0,
          avgDurationNs: 0,
          p99DurationNs: 0,
          maxDurationNs: 0,
        },
      ],
      edges: [],
    });

    expect(nodes[0].data.errorRate).toBe(0);
  });
});

describe('TopologyTab', () => {
  beforeEach(() => {
    graphInstances.length = 0;
    vi.mocked(getTraceTopology).mockReset();
    vi.mocked(getServiceSummary).mockReset();
  });

  it('shows the empty hint mentioning the Doris graph job when no data', async () => {
    vi.mocked(getTraceTopology).mockResolvedValue({
      code: 200,
      data: { nodes: [], edges: [] },
    });

    render(<TopologyTab clusterId={7} onShowTraces={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/otel_traces_graph_job/)).toBeTruthy();
    });
    expect(graphInstances).toHaveLength(0);
  });

  it('creates a graph and opens the service detail drawer on node click', async () => {
    vi.mocked(getTraceTopology).mockResolvedValue({
      code: 200,
      data: {
        nodes: [
          {
            serviceName: 'datasophon-api',
            spanCount: 10,
            errorCount: 0,
            avgDurationNs: 1_000_000,
            p99DurationNs: 2_000_000,
            maxDurationNs: 3_000_000,
          },
        ],
        edges: [],
      },
    });
    vi.mocked(getServiceSummary).mockResolvedValue({
      code: 200,
      data: {
        current: {
          spanCount: 10,
          errorCount: 0,
          avgDurationNs: 1_000_000,
          p99DurationNs: 2_000_000,
          maxDurationNs: 3_000_000,
        },
        previous: {
          spanCount: 8,
          errorCount: 0,
          avgDurationNs: 900_000,
          p99DurationNs: 1_800_000,
          maxDurationNs: 2_500_000,
        },
        series: [],
      },
    });
    const onShowTraces = vi.fn();

    render(<TopologyTab clusterId={7} onShowTraces={onShowTraces} />);

    await waitFor(() => {
      expect(graphInstances).toHaveLength(1);
    });
    act(() => {
      graphInstances[0].handlers['node:click']({
        target: { id: 'datasophon-api' },
      });
    });

    await waitFor(() => {
      expect(screen.getByText('查看 Traces')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('查看 Traces'));
    expect(onShowTraces).toHaveBeenCalledWith('datasophon-api');
  });
});
