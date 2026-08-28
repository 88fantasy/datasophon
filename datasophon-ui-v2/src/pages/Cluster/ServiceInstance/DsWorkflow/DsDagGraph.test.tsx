import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DsDagGraph from './DsDagGraph';

const { graphOptions, renderSpy, destroySpy } = vi.hoisted(() => ({
  graphOptions: { current: undefined as Record<string, any> | undefined },
  renderSpy: vi.fn().mockResolvedValue(undefined),
  destroySpy: vi.fn(),
}));

vi.mock('@antv/g6', () => ({
  Graph: class {
    constructor(options: Record<string, any>) {
      graphOptions.current = options;
    }
    render = renderSpy;
    fitView = vi.fn();
    resize = vi.fn();
    destroy = destroySpy;
    updateNodeData = vi.fn();
    updateEdgeData = vi.fn();
    draw = vi.fn().mockResolvedValue(undefined);
  },
}));

vi.mock('@/pages/Cluster/Lineage/flowingLineageEdge', () => ({
  FLOWING_LINEAGE_EDGE: 'flowing-edge',
}));

// 真实的 useIntl（react-intl）在 Provider 没变化时返回稳定引用；这里也返回同一个
// 单例对象，否则每次渲染都会产生新的 intl 引用，误触发依赖 labels 的 effect 重建。
const { intlSingleton } = vi.hoisted(() => ({
  intlSingleton: {
    formatMessage: ({
      id,
      defaultMessage,
    }: {
      id: string;
      defaultMessage?: string;
    }) => {
      if (id === 'dsWorkflow.metric.rows') return '行';
      if (id === 'dsWorkflow.metric.approximate') return '约';
      return defaultMessage ?? id;
    },
  },
}));

vi.mock('@umijs/max', () => ({
  useIntl: () => intlSingleton,
}));

const instance: DATASOPHON.DsWorkflowInstance = {
  id: 1,
  workflowCode: 2,
  name: 'synthetic',
  state: 'SUCCESS',
  durationSeconds: 1,
  dryRun: false,
};

describe('DsDagGraph node labels', () => {
  beforeEach(() => {
    graphOptions.current = undefined;
    vi.clearAllMocks();
  });

  it('separates batch outputs, stream zero rate, and unavailable metrics', () => {
    const dag: DATASOPHON.DsDag = {
      instance,
      nodes: [
        {
          taskCode: 1,
          name: 'batch',
          taskType: 'SPARK',
          flowType: 'BATCH',
          durationSeconds: 1,
          retryTimes: 0,
          metrics: {
            kind: 'BATCH',
            outputs: [
              { namespace: 'file', name: 'a', rowCount: 700, size: 1024 },
              { namespace: 'file', name: 'b', rowCount: 234, size: 2048 },
              { namespace: 'file', name: 'c', rowCount: 1, size: 1 },
            ],
          },
        },
        {
          taskCode: 2,
          name: 'stream',
          taskType: 'FLINK_STREAM',
          flowType: 'STREAM',
          durationSeconds: 1,
          retryTimes: 0,
          metrics: {
            kind: 'STREAM',
            rowsPerSecond: 0,
            approximate: true,
            processedApprox: 1234567,
          },
        },
        {
          taskCode: 3,
          name: 'unbound',
          taskType: 'SHELL',
          flowType: 'BATCH',
          durationSeconds: 1,
          retryTimes: 0,
          metricsError: 'NOT_BOUND',
        },
        {
          taskCode: 4,
          name: 'ended',
          taskType: 'FLINK_STREAM',
          flowType: 'STREAM',
          state: 'SUCCESS',
          durationSeconds: 1,
          retryTimes: 0,
          metricsError: 'JOB_ENDED',
        },
      ],
      edges: [
        { from: 1, to: 2 },
        { from: 2, to: 3 },
      ],
      locations: [],
    };

    render(<DsDagGraph dag={dag} />);

    const options = graphOptions.current;
    const labelText = options?.node.style.labelText as (datum: {
      data: DATASOPHON.DsDagNode;
    }) => string;
    expect(labelText({ data: dag.nodes[0] })).toContain('700 行 / 1 KB');
    expect(labelText({ data: dag.nodes[0] })).toContain('+1');
    expect(labelText({ data: dag.nodes[1] })).toContain('0.0 row/s');
    expect(labelText({ data: dag.nodes[1] })).toContain(
      'dsWorkflow.metric.processed 1,234,567 dsWorkflow.metric.items',
    );
    expect(labelText({ data: dag.nodes[2] })).toContain(
      'dsWorkflow.error.notBound',
    );
    expect(labelText({ data: dag.nodes[3] })).toContain(
      'dsWorkflow.status.jobEnded',
    );
    expect(options?.node.style.labelMaxLines).toBe(7);
    expect(
      options?.data.nodes.map((node: { id: string }) => node.id),
    ).not.toContain('0');
  });
});

describe('DsDagGraph incremental refresh vs structure change', () => {
  beforeEach(() => {
    graphOptions.current = undefined;
    vi.clearAllMocks();
  });

  const makeDag = (state: string): DATASOPHON.DsDag => ({
    instance,
    nodes: [
      {
        taskCode: 1,
        name: 'ods',
        taskType: 'SHELL',
        flowType: 'STREAM',
        durationSeconds: 1,
        retryTimes: 0,
        state,
      },
    ],
    edges: [],
    locations: [],
  });

  it('updates node data in place when only state changes, without rebuilding the graph', () => {
    const { rerender } = render(
      <DsDagGraph dag={makeDag('RUNNING_EXECUTION')} />,
    );
    const graphInstance = graphOptions.current;
    const destroyCallsBeforeRerender = destroySpy.mock.calls.length;

    rerender(<DsDagGraph dag={makeDag('SUCCESS')} />);

    // 结构没变：不应有新的 destroy/重建，画布还是同一个 Graph 实例。
    expect(destroySpy.mock.calls.length).toBe(destroyCallsBeforeRerender);
    expect(graphOptions.current).toBe(graphInstance);
    expect(
      screen.queryByText('dsWorkflow.dag.structureChanged'),
    ).not.toBeInTheDocument();
  });

  it('holds a structure change behind a confirmation prompt until the user refreshes', () => {
    const original = makeDag('RUNNING_EXECUTION');
    const { rerender } = render(<DsDagGraph dag={original} />);
    const destroyCallsBeforeRerender = destroySpy.mock.calls.length;
    const changed: DATASOPHON.DsDag = {
      ...original,
      nodes: [...original.nodes, { ...original.nodes[0], taskCode: 2 }],
    };

    rerender(<DsDagGraph dag={changed} />);

    // 结构变了但用户还没确认：画布保持旧结构，不重建。
    expect(destroySpy.mock.calls.length).toBe(destroyCallsBeforeRerender);
    expect(graphOptions.current?.data.nodes).toHaveLength(1);
    expect(
      screen.getByText('dsWorkflow.dag.structureChanged'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByText('dsWorkflow.dag.refreshCanvas'));

    // 用户确认后：重建一次画布，应用新结构，提示条消失。
    expect(destroySpy.mock.calls.length).toBe(destroyCallsBeforeRerender + 1);
    expect(graphOptions.current?.data.nodes).toHaveLength(2);
    expect(
      screen.queryByText('dsWorkflow.dag.structureChanged'),
    ).not.toBeInTheDocument();
  });
});
