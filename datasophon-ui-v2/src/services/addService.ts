import { request } from '@umijs/max';

// ─── 切片 7c：添加服务向导（物理集群） ───────────────────────────────────────

/** 按部署清单获取最新服务列表（清单中出现的服务带 selected=true）。 */
export function listNewestServices(
  clusterId: number,
  req: DATASOPHON.ManifestContext,
) {
  return request<{ data: DATASOPHON.FrameService[] }>(
    `/cluster/${clusterId}/add-service/list-newest`,
    { method: 'POST', data: req },
  );
}

/** 校验所选服务的依赖完整性（缺依赖时 errorMessage 给出原因）。 */
export function checkServiceDependency(clusterId: number, serviceIds: number[]) {
  return request<{ data: unknown }>(
    `/cluster/${clusterId}/add-service/check-dependency`,
    { method: 'POST', data: { serviceIds } },
  );
}

/** 获取 Master 角色列表（按部署清单回填 hosts）。 */
export function listMasterRoles(
  clusterId: number,
  req: DATASOPHON.ManifestContext & { serviceIds: number[] },
) {
  return request<{ data: DATASOPHON.FrameServiceRole[] }>(
    `/cluster/${clusterId}/add-service/service-roles`,
    { method: 'POST', data: req },
  );
}

/** 获取非 Master（Worker/Client）角色列表。 */
export function listNonMasterRoles(
  clusterId: number,
  req: DATASOPHON.ManifestContext & { serviceIds: number[] },
) {
  return request<{ data: DATASOPHON.FrameServiceRole[] }>(
    `/cluster/${clusterId}/add-service/non-master-roles`,
    { method: 'POST', data: req },
  );
}

/** 查询集群全部已纳管主机（角色分配候选）。 */
export function listManagedHosts(clusterId: number) {
  return request<{ data: DATASOPHON.HostInfo[] }>(
    `/cluster/${clusterId}/add-service/hosts`,
    { method: 'GET' },
  );
}

/** 保存服务角色与主机映射关系（Master/Worker 步共用）。 */
export function saveRoleHostMapping(
  clusterId: number,
  list: DATASOPHON.RoleHostMapping[],
) {
  return request<{ data: unknown }>(
    `/cluster/${clusterId}/add-service/role-host-mapping`,
    { method: 'POST', data: list },
  );
}

/** 从服务 DDL 定义读取配置项（未安装服务的初始配置）。 */
export function getConfigFromDdl(clusterId: number, serviceName: string) {
  return request<{ data: DATASOPHON.ConfigField[] }>(
    `/cluster/${clusterId}/add-service/config-from-ddl`,
    { method: 'GET', params: { serviceName } },
  );
}

/** 保存单个服务的配置（请求体形态与 ClusterServiceConfigV2Controller 一致）。 */
export function saveServiceConfig(
  clusterId: number,
  req: {
    serviceName: string;
    serviceConfig: DATASOPHON.ConfigField[];
    roleGroupId?: number;
  },
) {
  return request<{ data: unknown }>(
    `/cluster/${clusterId}/add-service/save-config`,
    { method: 'POST', data: req },
  );
}

/** 生成通用安装命令并立即执行 DAG，返回 dagId 供跳转 DAG 全屏图。 */
export function installServices(clusterId: number, serviceNames: string[]) {
  return request<{ data: DATASOPHON.DeployResult }>(
    `/cluster/${clusterId}/add-service/install`,
    { method: 'POST', data: { serviceNames } },
  );
}
