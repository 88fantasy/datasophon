import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  analyzePhysicalHosts,
  dispatchPhysicalWorkers,
  getPhysicalClusterInitializationStatus,
  retryPhysicalWorker,
  startPhysicalClusterInitialization,
} from './physicalHostInstall';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('physical host install service', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
    vi.mocked(request).mockResolvedValue({ code: 200, data: [] });
  });

  it('submits node connection details to the legacy host-check endpoint', async () => {
    await analyzePhysicalHosts(7, {
      hosts: 'ddh-01,ddh-02',
      sshUser: 'root',
      sshPass: 'secret',
      sshPort: 22,
    });

    expect(request).toHaveBeenCalledWith('/host/install/analysisHostList', {
      baseURL: '/ddh/api',
      method: 'POST',
      params: {
        clusterId: 7,
        hosts: 'ddh-01,ddh-02',
        sshUser: 'root',
        sshPass: 'secret',
        sshPort: 22,
        page: 1,
        pageSize: 100,
      },
    });
  });

  it('starts Worker distribution only through the explicit distribution endpoint', async () => {
    await dispatchPhysicalWorkers(7);

    expect(request).toHaveBeenCalledWith('/host/install/dispatcherHostAgentList', {
      baseURL: '/ddh/api',
      method: 'POST',
      params: { clusterId: 7, page: 1, pageSize: 100 },
    });
  });

  it('retries only the selected Worker host', async () => {
    await retryPhysicalWorker(7, 'ddh-03');

    expect(request).toHaveBeenCalledWith('/host/install/reStartDispatcherHostAgent', {
      baseURL: '/ddh/api',
      method: 'POST',
      params: { clusterId: 7, hostnames: 'ddh-03' },
    });
  });

  it('starts the physical initialization workflow through the v2 endpoint', async () => {
    await startPhysicalClusterInitialization(7);

    expect(request).toHaveBeenCalledWith('/cluster/7/initialization/start', {
      method: 'POST',
    });
  });

  it('loads resumable physical initialization status', async () => {
    await getPhysicalClusterInitializationStatus(7);

    expect(request).toHaveBeenCalledWith('/cluster/7/initialization/status', {
      method: 'GET',
    });
  });
});
