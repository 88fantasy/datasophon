import { listClusterHosts } from '@/services/host';
import { listAllK8sInstances, listK8sResources } from '@/services/k8s';
import {
  listClusterServices,
  listServiceRoleInstances,
} from '@/services/service';
import { getApiFailureMessage } from '@/utils/apiResponse';
import { BUILTIN_HOST_LABELS } from '@/utils/hostCategories';
import type {
  ResourceState,
  TopologyInstance,
  TopologyNode,
  TopologySnapshot,
  ZoneId,
} from './types';

const PAGE_SIZE = 100;

function responseData<T>(response: unknown, label: string): T {
  const failure = getApiFailureMessage(response, `${label}加载失败`);
  if (failure) throw new Error(failure);
  return (
    Array.isArray(response) ? response : (response as { data: T }).data
  ) as T;
}

function responseList<T>(response: unknown, label: string): T[] {
  const data = responseData<T[]>(response, label);
  if (!Array.isArray(data)) throw new Error(`${label}响应格式错误`);
  return data;
}

async function allPages<T extends { id: number }>(
  fetchPage: (page: number) => Promise<{ records: T[]; total: number }>,
  label: string,
  key: (row: T) => string | number = (row) => row.id,
): Promise<T[]> {
  const rows = new Map<string | number, T>();
  let total: number | undefined;
  for (let page = 1; ; page += 1) {
    const result = await fetchPage(page);
    if (
      !Array.isArray(result.records) ||
      !Number.isSafeInteger(result.total) ||
      result.total < 0
    ) {
      throw new Error(`${label}分页响应格式错误`);
    }
    if (total !== undefined && total !== result.total)
      throw new Error(`${label}在分页期间发生变化，请刷新重试`);
    total = result.total;
    const previousCount = rows.size;
    for (const row of result.records) {
      const id = row && key(row);
      if (
        !(typeof id === 'string'
          ? id.length > 0
          : Number.isSafeInteger(id) && id > 0)
      )
        throw new Error(`${label}记录标识无效`);
      rows.set(id, row);
    }
    if (rows.size >= total) return [...rows.values()];
    if (rows.size === previousCount)
      throw new Error(`${label}分页不完整（空页或重复页）`);
  }
}

function numericState(state: number | undefined): ResourceState {
  if (state === 1) return 'running';
  if (state === 2 || state === 5) return 'stopped';
  if (state === 3 || state === 4) return 'warning';
  return 'unknown';
}

function aggregateState(instances: TopologyInstance[]): ResourceState {
  const states = new Set(instances.map((instance) => instance.state));
  if (states.has('warning')) return 'warning';
  if (!states.size || states.has('unknown')) return 'unknown';
  if (states.size > 1) return 'warning';
  return instances[0].state;
}

function hostZone(label?: string): ZoneId {
  const category = BUILTIN_HOST_LABELS.find(
    (item) => item.value === label?.trim() || item.label === label?.trim(),
  );
  return category
    ? category.value === 'kubernetes'
      ? 'k8s'
      : category.value
    : 'other';
}

function text(value: unknown): string | undefined {
  return typeof value === 'string' && value.length ? value : undefined;
}

function servicePorts(value: unknown): string | undefined {
  if (!Array.isArray(value)) return text(value);
  return (
    value
      .flatMap((port: Record<string, unknown>) => {
        if (
          !port ||
          !Number.isInteger(port.port) ||
          Number(port.port) < 1 ||
          Number(port.port) > 65535
        )
          return [];
        return [
          [
            `${port.port}/${text(port.protocol) || 'TCP'}`,
            port.nodePort ? `NodePort ${port.nodePort}` : undefined,
            port.targetPort ? `目标端口 ${port.targetPort}` : undefined,
          ]
            .filter(Boolean)
            .join(' · '),
        ];
      })
      .join('；') || undefined
  );
}

/** 物理主机按 nodeLabel 归属；Kubernetes 展示节点及集群级 Service / CR。 */
export async function loadTopology(
  cluster: DATASOPHON.ClusterResponse,
): Promise<TopologySnapshot> {
  const clusterId = cluster.id;
  if (!Number.isSafeInteger(clusterId) || clusterId <= 0)
    throw new Error('集群 ID 无效');
  if (
    cluster.archType &&
    cluster.archType !== 'physical' &&
    cluster.archType !== 'k8s'
  )
    throw new Error('集群部署方式无效');
  const isK8s = cluster.archType === 'k8s';
  const snapshot: TopologySnapshot = {
    nodes: [],
    edges: [],
    warnings: [],
    observedAt: new Date().toISOString(),
  };
  const warn = (message: string) => snapshot.warnings.push(message);
  const scoped = <T extends { id: number; clusterId?: number }>(
    rows: T[],
    label: string,
    optionalClusterId = false,
    key: (row: T) => string | number = (row) => row.id,
  ): T[] => {
    const valid = rows.filter(
      (row) =>
        row &&
        Number.isSafeInteger(row.id) &&
        row.id >= (optionalClusterId && isK8s ? 0 : 1) &&
        (row.clusterId === clusterId ||
          (optionalClusterId && row.clusterId === undefined)),
    );
    if (valid.length !== rows.length)
      warn(
        `${label}已排除 ${rows.length - valid.length} 条非当前集群或标识无效的记录`,
      );
    const unique = [...new Map(valid.map((row) => [key(row), row])).values()];
    if (unique.length !== valid.length)
      warn(`${label}存在重复登记，已按资源标识合并`);
    return unique;
  };

  const inventory = await Promise.allSettled([
    allPages(
      async (page) =>
        responseData<DATASOPHON.HostPageResponse>(
          await listClusterHosts(clusterId, { page, pageSize: PAGE_SIZE }),
          '主机列表',
        ),
      '主机列表',
      (host) => (isK8s ? host.hostname : host.id),
    ),
    isK8s ? listAllK8sInstances(clusterId) : listClusterServices(clusterId),
  ]);
  const hostResult = inventory[0];
  const serviceResult = inventory[1];
  if (hostResult.status === 'rejected')
    throw new Error(
      `主机列表加载失败：${hostResult.reason instanceof Error ? hostResult.reason.message : '接口不可用'}`,
    );
  if (serviceResult.status === 'rejected')
    throw new Error('服务列表加载失败，无法生成完整拓扑');
  const hosts = scoped(hostResult.value, '主机列表', true, (host) =>
    isK8s ? host.hostname : host.id,
  );
  const hostsByName = new Map(hosts.map((host) => [host.hostname, host]));
  const hostNodes = new Map<string, TopologyNode>();

  const addHost = (hostname: string) => {
    const host = hostsByName.get(hostname);
    const zone = isK8s ? 'k8s' : hostZone(host?.nodeLabel);
    const resourceId =
      host && !isK8s
        ? `host:${clusterId}:${host.id}`
        : `host:${clusterId}:name:${hostname}`;
    const id = resourceId;
    const existing = hostNodes.get(id);
    if (existing) return existing;
    const node: TopologyNode = {
      id,
      resourceId,
      label: host?.ip || `${hostname}（IP 未登记）`,
      subtitle: host?.nodeLabel || (isK8s ? 'Kubernetes 节点' : '未配置类别'),
      zone,
      kind: 'host',
      source: 'inventory',
      state:
        isK8s && host?.hostState === 2
          ? 'warning'
          : numericState(host?.hostState),
      address: host?.ip || undefined,
      details: [
        { label: '主机名', value: hostname },
        {
          label: isK8s ? '节点角色' : '节点标签',
          value: host?.nodeLabel || '未配置',
        },
        ...(isK8s
          ? [
              {
                label: '节点状态',
                value:
                  host?.hostState === 1
                    ? 'Ready'
                    : host?.hostState === 2
                      ? 'NotReady'
                      : host?.hostState === 3
                        ? '存在告警'
                        : '未知',
              },
            ]
          : []),
        {
          label: '部署方式',
          value: isK8s
            ? 'Kubernetes 节点'
            : '主机部署（物理机 / VM 类型未登记）',
        },
        {
          label: '登记信息',
          value: host
            ? isK8s
              ? 'Kubernetes Node API'
              : `主机 ID ${host.id}`
            : '仅由角色的节点名称确认；IP 与硬件信息未知',
        },
        ...(host?.rack ? [{ label: '机架', value: host.rack }] : []),
        ...(host?.coreNum
          ? [{ label: 'CPU', value: `${host.coreNum} 核` }]
          : []),
        ...(host?.cpuArchitecture
          ? [{ label: '架构', value: host.cpuArchitecture }]
          : []),
      ],
      instances: [],
      href: host ? `/cluster/${clusterId}/host` : undefined,
    };
    if (!host) warn(`${hostname} 的主机登记信息缺失，仅展示已确认的节点名称`);
    hostNodes.set(id, node);
    snapshot.nodes.push(node);
    return node;
  };
  const failed = (label: string, reason: unknown) =>
    warn(
      `${label}加载失败：${reason instanceof Error ? reason.message : '接口不可用'}；相关数据不完整`,
    );

  if (!isK8s) {
    const services = scoped(
      responseList<DATASOPHON.ServiceInstanceInfo>(
        serviceResult.value,
        '服务列表',
      ),
      '服务列表',
    );
    const serviceNodes = services.map(
      (service): TopologyNode => ({
        id: `service:${clusterId}:${service.id}`,
        label: service.label || service.serviceName,
        serviceName: service.serviceName,
        subtitle: '主机部署',
        zone: 'other',
        kind: 'service',
        source: 'inventory',
        state: 'unknown',
        details: [
          { label: '服务名称', value: service.serviceName },
          { label: '部署方式', value: '主机部署（物理机 / VM 类型未登记）' },
          { label: '来源', value: '当前集群服务与角色登记' },
        ],
        instances: [],
        href: `/cluster/${clusterId}/service/${service.id}`,
      }),
    );
    for (let offset = 0; offset < services.length; offset += 3) {
      const batch = services.slice(offset, offset + 3);
      const results = await Promise.allSettled(
        batch.map((service) =>
          allPages(async (page) => {
            // 公共 wrapper 的旧类型误写为数组；后端实际返回分页 data.data 与 data.total。
            const result = responseData<{
              data: DATASOPHON.ServiceRoleInstanceInfo[];
              total: number;
            }>(
              await listServiceRoleInstances(clusterId, service.id, {
                page,
                pageSize: PAGE_SIZE,
              }),
              `${service.serviceName} 角色`,
            );
            return { records: result?.data, total: result?.total };
          }, `${service.serviceName} 角色`),
        ),
      );
      results.forEach((result, index) => {
        const service = batch[index];
        const node = serviceNodes[offset + index];
        if (result.status === 'rejected') {
          failed(`${node.label} 角色`, result.reason);
          return;
        }
        const clusterRoles = scoped(result.value, `${node.label} 角色`);
        const roles = clusterRoles.filter(
          (role) => role.serviceId === service.id,
        );
        if (roles.length !== clusterRoles.length)
          warn(
            `${node.label} 已排除 ${clusterRoles.length - roles.length} 条服务归属不符的角色`,
          );
        if (!roles.length)
          warn(`${node.label} 未登记可归属的角色，未显示服务器内部服务`);
        node.instances = roles.map((role) => ({
          id: `role:${clusterId}:${role.id}`,
          label: role.serviceRoleName,
          hostname: role.hostname || undefined,
          state: numericState(role.serviceRoleStateCode),
          ports:
            role.ports
              ?.filter(
                (port) =>
                  Number.isInteger(port.port) &&
                  port.port > 0 &&
                  port.port <= 65535,
              )
              .map((port) => `${port.label || port.paramName}: ${port.port}`)
              .join(' · ') || undefined,
        }));
        node.state = aggregateState(node.instances);
        for (const instance of node.instances) {
          if (!instance.hostname) {
            warn(
              `${node.label} / ${instance.label} 未登记部署主机，无法归入服务器`,
            );
            continue;
          }
          const host = addHost(instance.hostname);
          host.instances.push(instance);
          if (host.zone === 'k8s') continue;
          const id = `${node.id}:${host.id}`;
          let child = snapshot.nodes.find((item) => item.id === id);
          if (!child) {
            child = {
              ...node,
              id,
              parentId: host.id,
              zone: host.zone,
              instances: [],
            };
            snapshot.nodes.push(child);
          }
          child.instances.push(instance);
          child.state = aggregateState(child.instances);
          child.subtitle = [
            ...new Set(
              child.instances.map((item) => item.ports || '端口未登记'),
            ),
          ].join('；');
        }
      });
    }
  } else {
    const services = scoped(
      responseList<DATASOPHON.K8sServiceInstanceVO>(
        serviceResult.value,
        'K8s 服务列表',
      ),
      'K8s 服务列表',
    );
    for (let offset = 0; offset < services.length; offset += 3) {
      await Promise.all(
        services.slice(offset, offset + 3).map(async (service) => {
          const base: TopologyNode = {
            id: `cr:${clusterId}:${service.id}`,
            label: service.releaseName || service.serviceName,
            serviceName: service.serviceName,
            subtitle: `CR · ${service.namespace}`,
            zone: 'k8s',
            kind: 'service',
            source: 'inventory',
            state: service.missing ? 'warning' : 'unknown',
            details: [
              {
                label: '资源类型',
                value:
                  service.sourceKind === 'CR' ? 'CR（Operator）' : 'Service',
              },
              { label: '服务名称', value: service.serviceName },
              { label: '命名空间', value: service.namespace },
              {
                label: '登记状态',
                value: service.missing
                  ? '目标资源已缺失'
                  : '已登记；实时健康状态未知',
              },
            ],
            instances: [],
            href: `/cluster/${clusterId}/service/${service.id}`,
          };
          if (service.sourceKind === 'CR') {
            snapshot.nodes.push(base);
            return;
          }
          try {
            const endpoints = responseList<Record<string, unknown>>(
              await listK8sResources(clusterId, service.id, 'service'),
              `${service.serviceName} Service`,
            );
            if (!endpoints.length)
              warn(
                `${service.serviceName}（${service.namespace}）未返回 Service 资源`,
              );
            for (const endpoint of endpoints) {
              if (
                !endpoint ||
                !text(endpoint.name) ||
                (endpoint.clusterId !== undefined &&
                  endpoint.clusterId !== clusterId) ||
                (endpoint.namespace && endpoint.namespace !== service.namespace)
              ) {
                warn(
                  `${service.serviceName} 已排除名称无效或归属不符的 Service`,
                );
                continue;
              }
              const id = `k8s-service:${clusterId}:${service.namespace}:${endpoint.name}`;
              if (snapshot.nodes.some((node) => node.id === id)) {
                warn(
                  `${service.serviceName} 已跳过重复的 Service ${endpoint.name}`,
                );
                continue;
              }
              const ports = servicePorts(endpoint.ports) || '端口未登记';
              snapshot.nodes.push({
                ...base,
                id,
                label: String(endpoint.name),
                subtitle: `Service · ${ports}`,
                address: text(endpoint.clusterIP),
                details: [
                  ...base.details,
                  { label: '端口', value: ports },
                  {
                    label: '命名空间 / Service',
                    value: `${service.namespace}/${endpoint.name}`,
                  },
                  {
                    label: '地址',
                    value:
                      [
                        text(endpoint.clusterIP),
                        text(endpoint.externalIP),
                        text(endpoint.loadBalancerIP),
                      ]
                        .filter(Boolean)
                        .join(' · ') || '未提供',
                  },
                ],
              });
            }
          } catch (reason) {
            failed(`${service.serviceName} Service`, reason);
          }
        }),
      );
    }
  }
  for (const host of hosts) addHost(host.hostname);
  if (!isK8s) {
    if (
      snapshot.nodes.some((node) => node.kind === 'host' && node.zone === 'k8s')
    )
      warn(
        'Kubernetes 类别服务器已隐藏；当前物理集群接口不提供其内部 Service / CR，请进入已登记的 Kubernetes 集群查看',
      );
    snapshot.nodes = snapshot.nodes.filter(
      (node) => !(node.kind === 'host' && node.zone === 'k8s'),
    );
  }
  snapshot.warnings = [...new Set(snapshot.warnings)];
  snapshot.observedAt = new Date().toISOString();
  return snapshot;
}
