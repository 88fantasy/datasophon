import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getDsDag,
  getDsProjects,
  getDsWorkflowInstances,
  getDsWorkflows,
} from './dsWorkflow';

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

  it('keeps every DS read request inside the tab error boundary', async () => {
    vi.mocked(request).mockResolvedValue({ success: true, data: { list: [] } });

    await getDsWorkflows(7, 99, 2, 20, 'synthetic');
    await getDsWorkflowInstances(7, 99, 101, 10);
    await getDsDag(7, 99, 8);

    expect(request).toHaveBeenNthCalledWith(1, '/ds/workflows', {
      method: 'GET',
      params: {
        clusterId: 7,
        projectCode: 99,
        pageNo: 2,
        pageSize: 20,
        searchVal: 'synthetic',
      },
      skipErrorHandler: true,
    });
    expect(request).toHaveBeenNthCalledWith(2, '/ds/workflows/101/instances', {
      method: 'GET',
      params: { clusterId: 7, projectCode: 99, limit: 10 },
      skipErrorHandler: true,
    });
    expect(request).toHaveBeenNthCalledWith(3, '/ds/instances/8/dag', {
      method: 'GET',
      params: { clusterId: 7, projectCode: 99 },
      skipErrorHandler: true,
    });
  });
});
