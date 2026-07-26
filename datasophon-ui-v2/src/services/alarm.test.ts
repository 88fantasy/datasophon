import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listAlertHistory } from './alarm';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('alarm service', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
    vi.mocked(request).mockResolvedValue({
      data: { totalList: [], totalCount: 0 },
    });
  });

  it('queries alert history through the v2 cluster endpoint', async () => {
    const params = {
      alertTargetName: 'NameNode',
      hostname: 'node-1',
      alertLevel: 2,
      status: 1,
      startTime: '2026-07-01 00:00:00',
      endTime: '2026-07-26 23:59:59',
      page: 2,
      pageSize: 50,
    };

    await listAlertHistory(7, params);

    expect(request).toHaveBeenCalledWith('/cluster/7/alert/history/list', {
      method: 'GET',
      params,
    });
  });
});
