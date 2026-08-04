import { describe, expect, it } from 'vitest';
import { mergeExpansion, toG6Data } from './lineageGraphData';
import type { GraphData, GraphJob, NodeMeta } from './service';

function node(id: number, canonicalName: string, dwLayer: string | null = null): NodeMeta {
  return {
    id,
    clusterId: 7,
    connector: 'hive',
    catalogName: 'hive_catalog',
    databaseName: 'ods',
    tableName: canonicalName,
    canonicalName,
    dwLayer,
  };
}

function job(
  jobId: number,
  edgeId: number,
  overrides: Partial<GraphJob> = {},
): GraphJob {
  return {
    jobId,
    edgeId,
    flowType: 'OUTPUT',
    jobName: 'daily_orders_etl',
    lastRowCount: 1_200_000,
    lastBytes: 64_000_000,
    lastRunAt: '2026-08-04T03:01:44Z',
    runningAppId: null,
    ...overrides,
  };
}

describe('toG6Data', () => {
  it('marks the root node and expands a logical edge through its job node', () => {
    const graph: GraphData = {
      nodes: [node(1, 'a'), node(2, 'b')],
      edges: [{ src: 1, dst: 2, jobs: [job(10, 100)] }],
      collapsed: [],
      truncated: false,
    };

    const { nodes, edges } = toG6Data(graph, 1);

    expect(nodes).toEqual([
      expect.objectContaining({
        id: '1',
        data: expect.objectContaining({ isRoot: true }),
      }),
      expect.objectContaining({
        id: '2',
        data: expect.objectContaining({ isRoot: false }),
      }),
      expect.objectContaining({
        id: 'job:10',
        data: expect.objectContaining({
          jobName: 'daily_orders_etl',
          lastRowCount: 1_200_000,
          lastBytes: 64_000_000,
          lastRunAt: '2026-08-04T03:01:44Z',
          runningAppId: null,
        }),
      }),
    ]);
    expect(edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ source: '1', target: 'job:10' }),
        expect.objectContaining({ source: 'job:10', target: '2' }),
      ]),
    );
    expect(edges).toHaveLength(2);
  });

  it('deduplicates one job writing three tables into one job node and three outgoing edges', () => {
    const graph: GraphData = {
      nodes: [
        node(1, 'source'),
        node(2, 'output_a'),
        node(3, 'output_b'),
        node(4, 'output_c'),
      ],
      edges: [
        { src: 1, dst: 2, jobs: [job(10, 100, { lastRowCount: 10, lastBytes: 100, runningAppId: 'app-10' })] },
        { src: 1, dst: 3, jobs: [job(10, 101, { lastRowCount: 20, lastBytes: 200, runningAppId: 'app-10' })] },
        { src: 1, dst: 4, jobs: [job(10, 102, { lastRowCount: 30, lastBytes: 300, runningAppId: 'app-10' })] },
      ],
      collapsed: [],
      truncated: false,
    };

    const { nodes, edges } = toG6Data(graph, 1);

    expect(nodes.filter((n) => n.id === 'job:10')).toHaveLength(1);
    expect(
      edges.filter((edge) => edge.source === '1' && edge.target === 'job:10'),
    ).toHaveLength(1);
    expect(
      edges
        .filter((edge) => edge.source === 'job:10')
        .map((edge) => edge.target)
        .sort(),
    ).toEqual(['2', '3', '4']);
    expect(nodes.find((node) => node.id === 'job:10')?.data).toMatchObject({
      lastRowCount: null,
      lastBytes: null,
      lastRunAt: null,
      jobName: 'daily_orders_etl',
      runningAppId: 'app-10',
    });
    // P6：节点本身的统计置空后，Drawer 靠这份按目标表拆分的 outputs 显示真实数字，
    // 不能三个都是 null——否则点节点看到的和点某条出边看到的会互相矛盾。
    const outputs = nodes.find((n) => n.id === 'job:10')?.data.outputs as Array<{
      dstNodeId: number;
      dstName: string;
      lastRowCount: number | null;
      lastBytes: number | null;
    }>;
    expect(outputs.map((o) => o.dstNodeId).sort()).toEqual([2, 3, 4]);
    expect(outputs.find((o) => o.dstNodeId === 2)).toMatchObject({
      dstName: 'output_a',
      lastRowCount: 10,
      lastBytes: 100,
    });
    expect(outputs.find((o) => o.dstNodeId === 3)).toMatchObject({
      dstName: 'output_b',
      lastRowCount: 20,
      lastBytes: 200,
    });
    expect(outputs.find((o) => o.dstNodeId === 4)).toMatchObject({
      dstName: 'output_c',
      lastRowCount: 30,
      lastBytes: 300,
    });
  });

  it('keeps job statistics when multiple sources point to the same destination', () => {
    const graph: GraphData = {
      nodes: [node(1, 'source_a'), node(2, 'output'), node(3, 'source_b')],
      edges: [
        { src: 1, dst: 2, jobs: [job(10, 100)] },
        { src: 3, dst: 2, jobs: [job(10, 101)] },
      ],
      collapsed: [],
      truncated: false,
    };

    const { nodes } = toG6Data(graph, 1);

    const jobNodeData = nodes.find((node) => node.id === 'job:10')?.data;
    expect(jobNodeData).toMatchObject({
      lastRowCount: 1_200_000,
      lastBytes: 64_000_000,
      lastRunAt: '2026-08-04T03:01:44Z',
    });
    // 两条边指向同一张目标表，不应该在 outputs 里重复计一份——否则 hasMultipleDestinations
    // 会被误判为 true，节点自身的统计也会被错误地置空。
    expect(jobNodeData?.outputs).toHaveLength(1);
    expect((jobNodeData?.outputs as Array<{ dstNodeId: number }>)[0].dstNodeId).toBe(2);
  });

  it('adds a dashed placeholder node for each collapsed entry, oriented by direction', () => {
    const graph: GraphData = {
      nodes: [node(1, 'a')],
      edges: [],
      collapsed: [
        { type: 'collapsed', nodeId: 1, token: 'n:1:down:g3', hiddenCount: 5, direction: 'downstream' },
        { type: 'collapsed', nodeId: 1, token: 'n:1:up:g3', hiddenCount: 2, direction: 'upstream' },
      ],
      truncated: true,
    };

    const { nodes, edges } = toG6Data(graph, 1);

    const downstreamPlaceholder = nodes.find((n) => n.id === 'collapsed:n:1:down:g3');
    const upstreamPlaceholder = nodes.find((n) => n.id === 'collapsed:n:1:up:g3');
    expect(downstreamPlaceholder?.data).toMatchObject({ isCollapsedPlaceholder: true, hiddenCount: 5 });
    expect(upstreamPlaceholder?.data).toMatchObject({ isCollapsedPlaceholder: true, hiddenCount: 2 });

    const downstreamEdge = edges.find((e) => e.id === 'collapsed-edge:n:1:down:g3');
    expect(downstreamEdge).toMatchObject({ source: '1', target: 'collapsed:n:1:down:g3' });
    const upstreamEdge = edges.find((e) => e.id === 'collapsed-edge:n:1:up:g3');
    expect(upstreamEdge).toMatchObject({ source: 'collapsed:n:1:up:g3', target: '1' });
  });

  it('highlights nodes present in the impact set', () => {
    const graph: GraphData = {
      nodes: [node(1, 'a'), node(2, 'b')],
      edges: [],
      collapsed: [],
      truncated: false,
    };

    const { nodes } = toG6Data(graph, 1, new Set([2]));

    expect(nodes.find((n) => n.id === '1')?.data.impactHighlighted).toBe(false);
    expect(nodes.find((n) => n.id === '2')?.data.impactHighlighted).toBe(true);
  });
});

describe('mergeExpansion', () => {
  it('unions nodes/edges by id and drops the expanded token from collapsed', () => {
    const current: GraphData = {
      nodes: [node(1, 'a')],
      edges: [],
      collapsed: [
        { type: 'collapsed', nodeId: 1, token: 'n:1:down:g3', hiddenCount: 5, direction: 'downstream' },
      ],
      truncated: true,
    };
    const expansionResult: GraphData = {
      nodes: [node(1, 'a'), node(2, 'b'), node(3, 'c')],
      edges: [
        { src: 1, dst: 2, jobs: [] },
        { src: 1, dst: 3, jobs: [] },
      ],
      collapsed: [],
      truncated: false,
    };

    const merged = mergeExpansion(current, expansionResult, 'n:1:down:g3');

    expect(merged.nodes.map((n) => n.id).sort()).toEqual([1, 2, 3]);
    expect(merged.edges).toHaveLength(2);
    expect(merged.collapsed).toHaveLength(0);
    expect(merged.truncated).toBe(false);
  });

  it('keeps other collapsed placeholders untouched and appends newly discovered ones', () => {
    const current: GraphData = {
      nodes: [node(1, 'a'), node(5, 'e')],
      edges: [],
      collapsed: [
        { type: 'collapsed', nodeId: 1, token: 'n:1:down:g3', hiddenCount: 5, direction: 'downstream' },
        { type: 'collapsed', nodeId: 5, token: 'n:5:down:g3', hiddenCount: 9, direction: 'downstream' },
      ],
      truncated: true,
    };
    const expansionResult: GraphData = {
      nodes: [node(1, 'a')],
      edges: [],
      collapsed: [
        { type: 'collapsed', nodeId: 1, token: 'n:1:down:g4', hiddenCount: 400, direction: 'downstream' },
      ],
      truncated: true,
    };

    const merged = mergeExpansion(current, expansionResult, 'n:1:down:g3');

    expect(merged.collapsed.map((c) => c.token)).toEqual([
      'n:5:down:g3',
      'n:1:down:g4',
    ]);
  });
});
