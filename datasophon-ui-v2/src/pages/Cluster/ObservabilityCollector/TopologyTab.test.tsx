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
    data: { nodes?: Array<{ id: string; data?: unknown }> };

    constructor(options: { data?: { nodes?: Array<{ id: string; data?: unknown }> } }) {
      graphInstances.push(this);
      this.data = options.data ?? {};
    }

    on(name: string, handler: (event: unknown) => void) {
      this.handlers[name] = handler;
    }

    render() {}

    setData(data: { nodes?: Array<{ id: string; data?: unknown }> }) {
      this.data = data;
    }

    getNodeData(id: string) {
      return this.data.nodes?.find((node) => node.id === id);
    }

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

  it('marks external dependency nodes and derives a readable display name', () => {
    const { nodes } = toGraphData({
      nodes: [
        {
          serviceName: 'mysql@127.0.0.1:3306',
          spanCount: 4807,
          errorCount: 0,
          avgDurationNs: 800_000,
          p99DurationNs: 2_000_000,
          maxDurationNs: 5_000_000,
          external: true,
          dbSystem: 'mysql',
        },
      ],
      edges: [],
    });

    expect(nodes[0].id).toBe('mysql@127.0.0.1:3306');
    expect(nodes[0].data.external).toBe(true);
    expect(nodes[0].data.isDb).toBe(true);
    expect(nodes[0].data.displayName).toBe('mysql\n127.0.0.1:3306');
  });

  it('does not mark http/other/grpc external nodes as DB (only real db.system calls)', () => {
    // dbSystem 是 db.system → rpc.system → http → other 四级兜底的结果；worker/master/nexus 等外部
    // 依赖走的是普通 HTTP/gRPC 调用，dbSystem 落到 grpc/http/other，不应该显示 DB 徽标。
    const { nodes } = toGraphData({
      nodes: [
        {
          serviceName: 'grpc@192.168.10.131:18082',
          spanCount: 100,
          errorCount: 0,
          avgDurationNs: 500_000,
          p99DurationNs: 1_000_000,
          maxDurationNs: 2_000_000,
          external: true,
          dbSystem: 'grpc',
          serviceType: 'datasophon-worker',
        },
        {
          serviceName: 'other@192.168.10.131:8081',
          spanCount: 10,
          errorCount: 0,
          avgDurationNs: 500_000,
          p99DurationNs: 1_000_000,
          maxDurationNs: 2_000_000,
          external: true,
          dbSystem: 'other',
          serviceType: 'nexus',
        },
      ],
      edges: [],
    });

    expect(nodes[0].data.isDb).toBe(false);
    expect(nodes[1].data.isDb).toBe(false);
  });

  it('marks rpc.system=grpc external nodes as GRPC, not DB', () => {
    // api(18081)↔worker(18082) 之间是 gRPC 调用，span_attributes 里带 rpc.system=grpc（已在
    // ddh-01 真实 Doris 数据核实），后端把它纳入 db_system 四级兜底的第二档，前端应识别为 GRPC
    // 徽标而不是 DB 徽标。
    const { nodes } = toGraphData({
      nodes: [
        {
          serviceName: 'grpc@192.168.10.132:18082',
          spanCount: 200,
          errorCount: 0,
          avgDurationNs: 500_000,
          p99DurationNs: 1_000_000,
          maxDurationNs: 2_000_000,
          external: true,
          dbSystem: 'grpc',
          serviceType: 'datasophon-worker',
        },
      ],
      edges: [],
    });

    expect(nodes[0].data.isGrpc).toBe(true);
    expect(nodes[0].data.isDb).toBe(false);
  });

  it('prefers backend-resolved serviceType over dbSystem for icon and display name', () => {
    // 9030 端口的 db.system 上报为 mysql(Doris 走 MySQL 协议),但后端按 ip:port 反查集群实例表
    // 精确得出 serviceType=doris；展示名与图标应以 serviceType 为准，而不是被 dbSystem 误导成 mysql。
    const { nodes } = toGraphData({
      nodes: [
        {
          serviceName: 'mysql@192.168.10.131:9030',
          spanCount: 51843,
          errorCount: 0,
          avgDurationNs: 800_000,
          p99DurationNs: 2_000_000,
          maxDurationNs: 5_000_000,
          external: true,
          dbSystem: 'mysql',
          serviceType: 'doris',
        },
      ],
      edges: [],
    });

    expect(nodes[0].data.displayName).toBe('doris\n192.168.10.131:9030');
  });

  it('keeps both endpoints of error edges in error-only mode', () => {
    const { nodes, edges } = toGraphData(
      {
        nodes: [
          {
            serviceName: 'datasophon-api',
            spanCount: 100,
            errorCount: 0,
            avgDurationNs: 1_000_000,
            p99DurationNs: 2_000_000,
            maxDurationNs: 3_000_000,
          },
          {
            serviceName: 'mysql@127.0.0.1:3306',
            spanCount: 20,
            errorCount: 2,
            avgDurationNs: 1_500_000,
            p99DurationNs: 4_000_000,
            maxDurationNs: 5_000_000,
            external: true,
            dbSystem: 'mysql',
          },
          {
            serviceName: 'healthy-worker',
            spanCount: 50,
            errorCount: 0,
            avgDurationNs: 1_000_000,
            p99DurationNs: 2_000_000,
            maxDurationNs: 3_000_000,
          },
        ],
        edges: [
          {
            caller: 'datasophon-api',
            callee: 'mysql@127.0.0.1:3306',
            callCount: 20,
            errorCount: 2,
          },
          {
            caller: 'healthy-worker',
            callee: 'datasophon-api',
            callCount: 50,
            errorCount: 0,
          },
        ],
      },
      { onlyError: true },
    );

    expect(nodes.map((node) => node.id)).toEqual([
      'datasophon-api',
      'mysql@127.0.0.1:3306',
    ]);
    expect(edges).toHaveLength(1);
    expect(edges[0]).toMatchObject({
      source: 'datasophon-api',
      target: 'mysql@127.0.0.1:3306',
    });
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
