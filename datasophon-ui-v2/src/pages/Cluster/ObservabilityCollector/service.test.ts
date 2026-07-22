import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getCollectorMonitor, getTraceTopology } from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('ObservabilityCollector service', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
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
