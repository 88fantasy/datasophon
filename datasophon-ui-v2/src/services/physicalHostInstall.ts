import { request } from '@umijs/max';

const legacyRequestOptions = { baseURL: '/ddh/api' } as const;

export interface LegacyResult<T> {
  code: number;
  msg?: string;
  data?: T;
  total?: number;
  hostCheckCompleted?: boolean;
  dispatcherHostAgentCompleted?: boolean;
}

export interface HostCheckResult {
  code?: number;
  msg?: string;
}

export interface PhysicalHostInstallProgress {
  id: number;
  hostname: string;
  ip?: string;
  managed?: boolean;
  checkResult?: HostCheckResult;
  installStateCode?: number;
  progress?: number;
  message?: string;
}

export interface PhysicalHostConnection {
  hosts: string;
  sshUser: string;
  sshPass?: string;
  sshPort: number;
}

export type PhysicalInitializationPhase =
  | 'READY'
  | 'COLLECTOR_INSTALLING'
  | 'VERIFYING'
  | 'FAILED'
  | 'COMPLETED';

export interface PhysicalInitializationNode {
  hostname: string;
  ip: string;
  workerHealthy: boolean;
  collectorInstalled: boolean;
  collectorHealthy: boolean;
  message: string;
}

export interface PhysicalInitializationStatus {
  phase: PhysicalInitializationPhase;
  dagId?: string;
  completed: boolean;
  canRetry: boolean;
  nodes: PhysicalInitializationNode[];
}

export function analyzePhysicalHosts(
  clusterId: number,
  connection: PhysicalHostConnection,
) {
  return request<LegacyResult<PhysicalHostInstallProgress[]>>(
    '/host/install/analysisHostList',
    {
      ...legacyRequestOptions,
      method: 'POST',
      params: { clusterId, ...connection, page: 1, pageSize: 100 },
    },
  );
}

export function checkPhysicalHostsCompleted(clusterId: number) {
  return request<LegacyResult<null>>('/host/install/hostCheckCompleted', {
    ...legacyRequestOptions,
    method: 'POST',
    params: { clusterId },
  });
}

/**
 * 查询 Worker 分发状态。首次调用会启动所有已校验通过且尚未纳管主机的 Worker 分发。
 */
export function dispatchPhysicalWorkers(clusterId: number) {
  return request<LegacyResult<PhysicalHostInstallProgress[]>>(
    '/host/install/dispatcherHostAgentList',
    {
      ...legacyRequestOptions,
      method: 'POST',
      params: { clusterId, page: 1, pageSize: 100 },
    },
  );
}

export function checkPhysicalWorkersCompleted(clusterId: number) {
  return request<LegacyResult<null>>('/host/install/dispatcherHostAgentCompleted', {
    ...legacyRequestOptions,
    method: 'POST',
    params: { clusterId },
  });
}

export function retryPhysicalWorker(
  clusterId: number,
  hostname: string,
) {
  return request<LegacyResult<null>>('/host/install/reStartDispatcherHostAgent', {
    ...legacyRequestOptions,
    method: 'POST',
    params: { clusterId, hostnames: hostname },
  });
}

export function startPhysicalClusterInitialization(clusterId: number) {
  return request<{ data: PhysicalInitializationStatus }>(
    `/cluster/${clusterId}/initialization/start`,
    { method: 'POST' },
  );
}

export function getPhysicalClusterInitializationStatus(clusterId: number) {
  return request<{ data: PhysicalInitializationStatus }>(
    `/cluster/${clusterId}/initialization/status`,
    { method: 'GET' },
  );
}
