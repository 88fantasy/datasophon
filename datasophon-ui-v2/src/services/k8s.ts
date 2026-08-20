import { request } from '@umijs/max';

/** K8s 集群 namespace 列表（同时触发与 K8s 集群对账更新） */
export async function listK8sNamespaces(clusterId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sNamespace[]>>(
    `/cluster/${clusterId}/k8s/namespace/list`,
    { method: 'GET' },
  );
}

/** 指定 namespace 下的服务实例列表 */
export async function listK8sInstances(clusterId: number, namespace: string) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sServiceInstanceVO[]>>(
    `/cluster/${clusterId}/k8s/namespace/${namespace}/instance/list`,
    { method: 'GET' },
  );
}

/** 集群下全部服务实例（不分 namespace，同时触发与 K8s 集群对账更新） */
export async function listAllK8sInstances(clusterId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sServiceInstanceVO[]>>(
    `/cluster/${clusterId}/k8s/instance/list`,
    { method: 'GET' },
  );
}

/** 单个服务实例详情（比列表接口多带 metricsJob，监控 Tab 靠它限定 job 过滤） */
export async function getK8sInstance(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sServiceInstanceVO>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}`,
    { method: 'GET' },
  );
}

/** 服务实例支持的资源类型列表（Pod / Service / Deployment / Ingress / ConfigMap 等） */
export async function listK8sResourceTypes(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<string[]>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/resource-types`,
    { method: 'GET' },
  );
}

/** 服务实例指定资源类型的资源列表 */
export async function listK8sResources(
  clusterId: number,
  instanceId: number,
  resourceType: string,
) {
  return request<DATASOPHON.ApiResponse<Record<string, unknown>[]>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/resource`,
    { method: 'GET', params: { resourceType } },
  );
}

export async function getK8sDashboard(clusterId: number, range = '24h') {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sDashboardResponse>>(
    `/cluster/${clusterId}/k8s/dashboard`,
    { method: 'GET', params: { range } },
  );
}

/** K8s Helm values 版本列表（仅含 id / version，降序） */
export async function listK8sConfigVersions(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sInstanceValuesSimple[]>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/config/versions`,
    { method: 'GET' },
  );
}

/** 读取指定版本的完整 Helm values（含 values / deltaValues / metaFileType） */
export async function getK8sConfig(clusterId: number, instanceId: number, valueId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sInstanceValues>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/config/${valueId}`,
    { method: 'GET' },
  );
}

/** 保存用户编辑的 deltaValues（仅更新当前版本，不升版、不打 needRestart） */
export async function saveK8sConfig(
  clusterId: number,
  instanceId: number,
  body: { id: number; deltaValues: string },
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/config`,
    { method: 'POST', data: body },
  );
}

// ── 集群接管（takeover）────────────────────────────────────────────

/** 扫描目标集群已存在的服务，按 chart 名匹配框架服务定义 */
export async function scanTakeover(clusterId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sTakeoverScanResult>>(
    `/cluster/${clusterId}/k8s/takeover/scan`,
    { method: 'GET' },
  );
}

/** 发现 Doris 数据源候选地址 */
export async function listDorisCandidates(clusterId: number) {
  return request<
    DATASOPHON.ApiResponse<DATASOPHON.DorisDatasourceCandidate[]>
  >(`/cluster/${clusterId}/k8s/takeover/doris/candidates`, { method: 'GET' });
}

/** 测试 Doris 连通性，不落库 */
export async function testDorisDatasource(
  clusterId: number,
  body: {
    host: string;
    port?: number;
    password: string;
  },
) {
  return request<DATASOPHON.ApiResponse<string>>(
    `/cluster/${clusterId}/k8s/takeover/doris/test`,
    { method: 'POST', data: body },
  );
}

/** 保存 Doris 数据源，连通性测试不通过则拒绝保存 */
export async function saveDorisDatasource(
  clusterId: number,
  body: {
    host: string;
    port?: number;
    password: string;
  },
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/k8s/takeover/doris`,
    { method: 'POST', data: body },
  );
}

/** 提交接管登记，并探测各服务的 OTel job */
export async function registerTakeover(
  clusterId: number,
  bindings: Array<{
    releaseName: string;
    namespace: string;
    frameServiceId: number;
    sourceKind?: 'HELM' | 'CR';
  }>,
) {
  return request<
    DATASOPHON.ApiResponse<DATASOPHON.K8sTakeoverRegisterResult[]>
  >(`/cluster/${clusterId}/k8s/takeover/register`, {
    method: 'POST',
    data: { bindings },
  });
}

/** 取消接管：只移除平台登记记录，不影响目标集群 */
export async function cancelTakeover(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/k8s/takeover/instance/${instanceId}`,
    { method: 'DELETE' },
  );
}

/** 只读反查接管实例的 helm values（helm get values 的原文） */
export async function readTakeoverValues(
  clusterId: number,
  instanceId: number,
) {
  return request<DATASOPHON.ApiResponse<string>>(
    `/cluster/${clusterId}/k8s/takeover/instance/${instanceId}/values`,
    { method: 'GET' },
  );
}
