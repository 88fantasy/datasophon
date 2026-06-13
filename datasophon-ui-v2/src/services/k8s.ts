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
