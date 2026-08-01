import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getGraph,
  getImpact,
  getJob,
  getOverview,
  getTable,
  listTables,
  rebuild,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('Lineage service', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
  });

  it('lists tables, drops blank filter params, and unwraps the V2ResponseBodyAdvice envelope', async () => {
    // com.datasophon.api.controller.v2.V2ResponseBodyAdvice 把控制器返回值统一包一层
    // ApiResponse{success,data}，控制器本身返回的 LineageQueryResponse{data,snapshot,
    // sourceFreshness} 因此是 data.data 双层嵌套——mock 必须还原真实响应体的形状，
    // 否则测不出 service.ts 有没有正确解包。
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: { list: [], total: 0 },
        snapshot: { generation: 1 },
        sourceFreshness: { status: 'OK' },
      },
    });

    const result = await listTables({
      clusterId: 7,
      page: 2,
      size: 20,
      keyword: '',
      layer: 'DWD',
      connector: undefined,
      database: undefined,
    });

    expect(request).toHaveBeenCalledWith('/lineage/tables', {
      method: 'GET',
      params: { clusterId: 7, page: 2, size: 20, layer: 'DWD' },
    });
    // 返回值必须是解包后的 LineageQueryResponse，不能是外层信封本身。
    expect(result).toEqual({
      data: { list: [], total: 0 },
      snapshot: { generation: 1 },
      sourceFreshness: { status: 'OK' },
    });
  });

  it('queries the graph with depth/direction and forwards skipErrorHandler', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: { nodes: [], edges: [], collapsed: [], truncated: false },
        snapshot: {},
        sourceFreshness: {},
      },
    });

    const result = await getGraph(
      { clusterId: 7, rootNodeId: 42, depth: 3, direction: 'both' },
      { skipErrorHandler: true },
    );

    expect(request).toHaveBeenCalledWith('/lineage/graph', {
      method: 'GET',
      params: { clusterId: 7, rootNodeId: 42, depth: 3, direction: 'both' },
      skipErrorHandler: true,
    });
    expect(result.data).toEqual({
      nodes: [],
      edges: [],
      collapsed: [],
      truncated: false,
    });
  });

  it('sends the expand token as a query param when expanding a collapsed node', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        data: { nodes: [], edges: [], collapsed: [], truncated: false },
        snapshot: {},
        sourceFreshness: {},
      },
    });

    await getGraph({
      clusterId: 7,
      rootNodeId: 42,
      expand: 'n:99:down:g3',
    });

    expect(request).toHaveBeenCalledWith('/lineage/graph', {
      method: 'GET',
      params: { clusterId: 7, rootNodeId: 42, expand: 'n:99:down:g3' },
    });
  });

  it('queries overview and single-table detail by clusterId', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { data: {}, snapshot: {}, sourceFreshness: {} },
    });

    await getOverview(7);
    expect(request).toHaveBeenCalledWith('/lineage/overview', {
      method: 'GET',
      params: { clusterId: 7 },
    });

    await getTable(7, 42);
    expect(request).toHaveBeenCalledWith('/lineage/table/42', {
      method: 'GET',
      params: { clusterId: 7 },
    });
  });

  it('fetches job detail, forwards skipErrorHandler, and unwraps the envelope (job endpoint is not a LineageQueryResponse)', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { id: 100, clusterId: 7, jobName: 'sync_orders' },
    });

    const result = await getJob(7, 100, { skipErrorHandler: true });

    expect(request).toHaveBeenCalledWith('/lineage/job/100', {
      method: 'GET',
      params: { clusterId: 7 },
      skipErrorHandler: true,
    });
    expect(result).toEqual({ id: 100, clusterId: 7, jobName: 'sync_orders' });
  });

  it('queries impact analysis (downstream-only) with skipErrorHandler forwarded', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { data: {}, snapshot: {}, sourceFreshness: {} },
    });

    await getImpact(
      { clusterId: 7, rootNodeId: 42, depth: 2 },
      { skipErrorHandler: true },
    );

    expect(request).toHaveBeenCalledWith('/lineage/impact', {
      method: 'GET',
      params: { clusterId: 7, rootNodeId: 42, depth: 2 },
      skipErrorHandler: true,
    });
  });

  it('posts a manual rebuild for the given cluster and unwraps the envelope', async () => {
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { generation: 5 },
    });

    const result = await rebuild(7);

    expect(request).toHaveBeenCalledWith('/lineage/rebuild', {
      method: 'POST',
      params: { clusterId: 7 },
    });
    expect(result).toEqual({ generation: 5 });
  });
});
