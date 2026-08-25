import { render } from '@testing-library/react';
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
    destroy = destroySpy;
  },
}));

vi.mock('@/pages/Cluster/Lineage/flowingLineageEdge', () => ({
  FLOWING_LINEAGE_EDGE: 'flowing-edge',
}));

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
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
  }),
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
          metrics: { kind: 'STREAM', rowsPerSecond: 0, approximate: true },
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
    expect(labelText({ data: dag.nodes[2] })).toMatch(/—$/);
    expect(options?.node.style.labelMaxLines).toBe(6);
    expect(
      options?.data.nodes.map((node: { id: string }) => node.id),
    ).not.toContain('0');
  });
});
