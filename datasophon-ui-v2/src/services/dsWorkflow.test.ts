import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDsProjects } from './dsWorkflow';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('DS workflow service', () => {
  beforeEach(() => vi.clearAllMocks());

  it('keeps a DS 401 inside the workflow tab instead of triggering global logout', async () => {
    vi.mocked(request).mockResolvedValue({ success: true, data: { list: [] } });

    await getDsProjects(7);

    expect(request).toHaveBeenCalledWith('/ds/projects', {
      method: 'GET',
      params: { clusterId: 7 },
      skipErrorHandler: true,
    });
  });
});
