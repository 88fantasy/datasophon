import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getGraph,
  getImpact,
  getJob,
  getJobMetrics,
  getJobRateHistory,
  getOverview,
  getTable,
  listTables,
  rebuild,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

/**
 * Cross-repo contract test: catches Gravitino renaming/dropping a response field that
 * datasophon-ui-v2's service.ts interfaces (and GravitinoLineageClient's clusterId injection)
 * silently depend on. TypeScript interfaces are erased at runtime, so nothing else in this
 * repo would fail a build when a field goes missing — only a real property-presence check on
 * a fixture shaped like the actual wire response catches it.
 *
 * Fixtures mirror gravitino/docs/open-api/lineage.yaml's `required` field lists for
 * LineageNode / LineageEdge / LineageJobReference / LineageCollapsedNode /
 * LineageSnapshotFreshness / LineageSourceFreshness / LineageTablePage / LineageJobDetail /
 * LineageRebuildAccepted, plus the `clusterId` field GravitinoLineageClient injects into every
 * node object (which is NOT part of Gravitino's own schema — it only exists once the proxy has
 * touched the response, so a fixture without it would prove nothing about the real pipeline).
 */
describe('Lineage proxy response contract', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
  });

  const node = {
    id: 42,
    clusterId: 7,
    namespace: 'mysql://192.168.10.131:3306',
    name: 'lineage_acceptance.dwd_orders',
    connector: 'mysql',
    catalogName: 'lineage_acceptance',
    databaseName: 'lineage_acceptance',
    tableName: 'dwd_orders',
    canonicalName: 'mysql://192.168.10.131:3306/lineage_acceptance/dwd_orders',
    dwLayer: 'DWD',
  };

  const snapshotFreshness = {
    generation: 16,
    targetGeneration: 16,
    builtAt: '2026-08-01T02:00:00Z',
    ageSeconds: 3,
    stale: false,
    lastRebuildError: null,
  };

  const sourceFreshness = { lastEventReceivedAt: '2026-08-01T01:59:00Z', status: 'OK' };

  function expectRequiredNodeFields(actual: object) {
    for (const field of [
      'id',
      'clusterId',
      'connector',
      'catalogName',
      'databaseName',
      'tableName',
      'canonicalName',
    ]) {
      expect(actual, `node is missing required field "${field}"`).toHaveProperty(field);
    }
  }

  function expectRequiredSnapshotFields(actual: object) {
    for (const field of ['generation', 'targetGeneration', 'builtAt', 'ageSeconds', 'stale']) {
      expect(actual, `snapshot is missing required field "${field}"`).toHaveProperty(field);
    }
  }

  it('tables: LineageTableQueryResponse required fields survive the envelope unwrap', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: { list: [node], total: 1 },
        snapshot: snapshotFreshness,
        sourceFreshness,
      },
    });

    const result = await listTables({ clusterId: 7 });

    expect(result.data).toHaveProperty('list');
    expect(result.data).toHaveProperty('total');
    expectRequiredNodeFields(result.data.list[0]);
    expectRequiredSnapshotFields(result.snapshot);
    expect(result.sourceFreshness).toHaveProperty('status');
  });

  it('graph: LineageGraphQueryResponse required fields (nodes/edges/collapsed) survive', async () => {
    const edge = {
      src: 1,
      dst: 42,
      jobs: [{
        jobId: 10,
        edgeId: 100,
        flowType: 'TABLE',
        jobName: 'sync_orders',
        lastRowCount: null,
        lastBytes: null,
        lastRunAt: null,
        runningAppId: null,
      }],
    };
    const collapsedNode = {
      type: 'collapsed',
      nodeId: 1,
      token: 'n:1:down:g16',
      hiddenCount: 320,
      direction: 'downstream',
    };
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: { nodes: [node], edges: [edge], collapsed: [collapsedNode], truncated: true },
        snapshot: snapshotFreshness,
        sourceFreshness,
      },
    });

    const result = await getGraph({ clusterId: 7, rootNodeId: 42 });

    expectRequiredNodeFields(result.data.nodes[0]);
    for (const field of ['src', 'dst', 'jobs']) {
      expect(result.data.edges[0], `edge is missing required field "${field}"`).toHaveProperty(
        field,
      );
    }
    for (const field of ['jobId', 'edgeId', 'flowType']) {
      expect(
        result.data.edges[0].jobs[0],
        `job reference is missing required field "${field}"`,
      ).toHaveProperty(field);
    }
    for (const field of ['type', 'nodeId', 'token', 'hiddenCount', 'direction']) {
      expect(
        result.data.collapsed[0],
        `collapsed node is missing required field "${field}"`,
      ).toHaveProperty(field);
    }
    expect(result.data).toHaveProperty('truncated');
  });

  it('impact: shares LineageGraphQueryResponse required fields with graph', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: { nodes: [node], edges: [], collapsed: [], truncated: false },
        snapshot: snapshotFreshness,
        sourceFreshness,
      },
    });

    const result = await getImpact({ clusterId: 7, rootNodeId: 42 });

    expectRequiredNodeFields(result.data.nodes[0]);
    expectRequiredSnapshotFields(result.snapshot);
  });

  it('table: LineageNodeQueryResponse required node fields survive', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { data: node, snapshot: snapshotFreshness, sourceFreshness },
    });

    const result = await getTable(7, 42);

    expectRequiredNodeFields(result.data);
  });

  it('overview: layer/edge summary fields the frontend consumes survive', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: {
          layers: [{ layer: 'DWD', nodeCount: 12 }],
          edges: [{ srcLayer: 'ODS', dstLayer: 'DWD', count: 5 }],
        },
        snapshot: snapshotFreshness,
        sourceFreshness,
      },
    });

    const result = await getOverview(7);

    expect(result.data.layers[0]).toEqual({ layer: 'DWD', nodeCount: 12 });
    expect(result.data.edges[0]).toEqual({ srcLayer: 'ODS', dstLayer: 'DWD', count: 5 });
  });

  it('job: LineageJobDetail required fields survive (not wrapped in a QueryResponse)', async () => {
    const jobDetail = {
      id: 100,
      clusterId: 7,
      jobName: 'sync_orders',
      engine: 'SPARK',
      jobType: 'BATCH',
      dwLayer: 'DWD',
      owner: null,
      externalUrl: null,
      state: 'COMPLETE',
      updateTime: '2026-08-01T02:00:00Z',
    };
    vi.mocked(request).mockResolvedValue({ success: true, data: jobDetail });

    const result = await getJob(7, 100);

    for (const field of ['id', 'jobName', 'engine', 'jobType', 'state', 'updateTime']) {
      expect(result, `job detail is missing required field "${field}"`).toHaveProperty(field);
    }
  });

  it('rebuild: LineageRebuildAccepted required field survives', async () => {
    vi.mocked(request).mockResolvedValue({ success: true, data: { generation: 17 } });

    const result = await rebuild(7);

    expect(result).toHaveProperty('generation');
  });

  it('job metrics: sends app ids as a comma-separated contract parameter', async () => {
    const metrics = {
      completeTasks: 12,
      activeTasks: 2,
      recordsWritten: 60_000_000,
      bytesWritten: 2_204_955_464,
      recordsWrittenRate: 51_234.5,
      runningStages: 1,
      sampledAt: '2026-08-04T03:01:44Z',
    };
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { 'application_1': metrics },
    });

    const result = await getJobMetrics(7, ['application_1', 'application_2']);

    expect(request).toHaveBeenCalledWith('/lineage/job-metrics', {
      method: 'GET',
      params: { clusterId: 7, appIds: 'application_1,application_2' },
      skipErrorHandler: true,
    });
    expect(result.application_1).toEqual(metrics);
  });

  it('job rate history: queries app_id range and sums executor series by timestamp', async () => {
    const nowSpy = vi
      .spyOn(Date, 'now')
      .mockReturnValue(1_800_000_000_000);
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        resultType: 'matrix',
        result: [
          { metric: { instance: '1' }, values: [[1_799_999_940, '10'], [1_800_000_000, '20']] },
          { metric: { instance: '2' }, values: [[1_799_999_940, '3'], [1_800_000_000, '4']] },
        ],
      },
    });

    const result = await getJobRateHistory(7, 'application_1');

    expect(request).toHaveBeenCalledWith('/observability/otel/metrics/query_range', {
      method: 'GET',
      params: {
        clusterId: 7,
        metric: 'spark_executor_recordsWritten',
        rateWindow: '1m',
        table: 'sum',
        filters: 'app_id:application_1',
        start: 1_799_996_400,
        end: 1_800_000_000,
        step: 60,
      },
      skipErrorHandler: true,
    });
    expect(result).toEqual([
      { time: 1_799_999_940_000, value: 13 },
      { time: 1_800_000_000_000, value: 24 },
    ]);
    nowSpy.mockRestore();
  });
});
