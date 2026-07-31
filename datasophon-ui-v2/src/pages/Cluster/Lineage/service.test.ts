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

  it('lists tables and drops blank filter params', async () => {
    vi.mocked(request).mockResolvedValue({
      data: { list: [], total: 0 },
      snapshot: {},
      sourceFreshness: {},
    });

    await listTables({
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
  });

  it('queries the graph with depth/direction and forwards skipErrorHandler', async () => {
    vi.mocked(request).mockResolvedValue({
      data: { nodes: [], edges: [], collapsed: [], truncated: false },
      snapshot: {},
      sourceFreshness: {},
    });

    await getGraph(
      { clusterId: 7, rootNodeId: 42, depth: 3, direction: 'both' },
      { skipErrorHandler: true },
    );

    expect(request).toHaveBeenCalledWith('/lineage/graph', {
      method: 'GET',
      params: { clusterId: 7, rootNodeId: 42, depth: 3, direction: 'both' },
      skipErrorHandler: true,
    });
  });

  it('sends the expand token as a query param when expanding a collapsed node', async () => {
    vi.mocked(request).mockResolvedValue({
      data: { nodes: [], edges: [], collapsed: [], truncated: false },
      snapshot: {},
      sourceFreshness: {},
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
    vi.mocked(request).mockResolvedValue({});

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

  it('fetches job detail with skipErrorHandler forwarded', async () => {
    vi.mocked(request).mockResolvedValue({});

    await getJob(7, 100, { skipErrorHandler: true });

    expect(request).toHaveBeenCalledWith('/lineage/job/100', {
      method: 'GET',
      params: { clusterId: 7 },
      skipErrorHandler: true,
    });
  });

  it('queries impact analysis (downstream-only) with skipErrorHandler forwarded', async () => {
    vi.mocked(request).mockResolvedValue({});

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

  it('posts a manual rebuild for the given cluster', async () => {
    vi.mocked(request).mockResolvedValue({ generation: 5 });

    await rebuild(7);

    expect(request).toHaveBeenCalledWith('/lineage/rebuild', {
      method: 'POST',
      params: { clusterId: 7 },
    });
  });
});
