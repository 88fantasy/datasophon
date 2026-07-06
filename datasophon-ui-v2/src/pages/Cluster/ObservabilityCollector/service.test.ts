import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getCollectorConfig,
  getCollectorMonitor,
  getTraceTopology,
  pushCollectorConfig,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('ObservabilityCollector service', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
  });

  it('loads configuration through the legacy observability API', async () => {
    vi.mocked(request).mockResolvedValue({ code: 200, data: [] });

    await getCollectorConfig(7);

    expect(request).toHaveBeenCalledWith('/observability/otelcol/config', {
      baseURL: '/ddh/api',
      method: 'GET',
      params: { clusterId: 7 },
    });
  });

  it('pushes complete parameters to one node', async () => {
    vi.mocked(request).mockResolvedValue({ code: 200 });

    await pushCollectorConfig(7, 'worker-1', { batchSize: '4096' });

    expect(request).toHaveBeenCalledWith('/observability/otelcol/push', {
      baseURL: '/ddh/api',
      method: 'POST',
      params: { clusterId: 7, hostname: 'worker-1' },
      data: { batchSize: '4096' },
    });
  });

  it('loads trace topology with the time window as query params', async () => {
    vi.mocked(request).mockResolvedValue({
      code: 200,
      data: { nodes: [], edges: [] },
    });

    await getTraceTopology(7, 100, 200);

    expect(request).toHaveBeenCalledWith(
      '/observability/otelcol/traces/topology',
      {
        baseURL: '/ddh/api',
        method: 'GET',
        params: { clusterId: 7, start: 100, end: 200 },
      },
    );
  });

  it('loads node metrics independently', async () => {
    vi.mocked(request).mockResolvedValue({ code: 200, data: [] });

    await getCollectorMonitor(7);

    expect(request).toHaveBeenCalledWith('/observability/otelcol/monitor', {
      baseURL: '/ddh/api',
      method: 'GET',
      params: { clusterId: 7 },
    });
  });
});
