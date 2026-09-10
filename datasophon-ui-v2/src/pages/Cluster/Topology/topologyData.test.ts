import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listClusterHosts } from '@/services/host';
import { listAllK8sInstances, listK8sResources } from '@/services/k8s';
import {
  listClusterServices,
  listServiceRoleInstances,
} from '@/services/service';
import { loadTopology } from './topologyData';

vi.mock('@/services/host', () => ({ listClusterHosts: vi.fn() }));
vi.mock('@/services/k8s', () => ({
  listAllK8sInstances: vi.fn(),
  listK8sResources: vi.fn(),
}));
vi.mock('@/services/service', () => ({
  listClusterServices: vi.fn(),
  listServiceRoleInstances: vi.fn(),
}));

const cluster: DATASOPHON.ClusterResponse = {
  id: 7,
  clusterName: '测试集群',
  clusterCode: 'test',
  archType: 'physical',
};
const host = (id: number, clusterId = 7) => ({
  id,
  clusterId,
  hostname: `node-${id}`,
  ip: `10.0.0.${id}`,
  hostState: 1,
});
const service = (id: number, serviceName: string, clusterId = 7) => ({
  id,
  clusterId,
  serviceName,
  label: serviceName,
  serviceStateCode: 1,
});
const role = (id: number, serviceId = 1) => ({
  id,
  clusterId: 7,
  serviceId,
  serviceRoleName: `role-${id}`,
  hostname: 'node-1',
  serviceRoleStateCode: 1,
  ports: [{ paramName: 'http', label: 'HTTP', port: 8030 }],
});

describe('loadTopology', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listClusterHosts).mockResolvedValue({
      data: { records: [host(1)], total: 1 },
    });
    vi.mocked(listClusterServices).mockResolvedValue({ data: [] });
    vi.mocked(listAllK8sInstances).mockResolvedValue({ data: [] } as never);
    vi.mocked(listServiceRoleInstances).mockResolvedValue({
      data: { data: [], total: 0 },
    } as never);
    vi.mocked(listK8sResources).mockResolvedValue({ data: [] } as never);
  });

  it('assigns each host once by nodeLabel, with unlabeled and custom labels in other', async () => {
    vi.mocked(listClusterHosts).mockResolvedValue({
      data: {
        records: [
          { ...host(1), nodeLabel: 'edge' },
          { ...host(2), nodeLabel: 'Doris集群' },
          { ...host(3), nodeLabel: 'vm' },
          host(4),
          { ...host(5), nodeLabel: 'constructor' },
        ],
        total: 5,
      },
    });
    vi.mocked(listClusterServices).mockResolvedValue({
      data: [service(1, 'DORIS')],
    } as never);
    vi.mocked(listServiceRoleInstances).mockResolvedValue({
      data: { data: [role(1)], total: 1 },
    } as never);
    const result = await loadTopology(cluster);
    const hosts = result.nodes.filter((n) => n.kind === 'host');
    expect(hosts.map((n) => [n.label, n.zone])).toEqual([
      ['10.0.0.1', 'edge'],
      ['10.0.0.2', 'doris'],
      ['10.0.0.3', 'vm'],
      ['10.0.0.4', 'other'],
      ['10.0.0.5', 'other'],
    ]);
    const middleware = result.nodes.find((n) => n.kind === 'service');
    expect(middleware).toMatchObject({ parentId: hosts[0].id, zone: 'edge' });
    expect(middleware?.subtitle).toContain('8030');
  });

  it('loads all host and role pages beyond 100 without inventing communication links', async () => {
    const hosts = Array.from({ length: 105 }, (_, i) => host(i + 1));
    const roles = Array.from({ length: 103 }, (_, i) => role(i + 1));
    vi.mocked(listClusterHosts).mockImplementation(
      async (_, { page, pageSize }) => ({
        data: {
          records: hosts.slice((page - 1) * pageSize, page * pageSize),
          total: hosts.length,
        },
      }),
    );
    vi.mocked(listClusterServices).mockResolvedValue({
      data: [service(1, 'DORIS')],
    } as never);
    vi.mocked(listServiceRoleInstances).mockImplementation(
      async (_, __, { page = 1, pageSize = 100 }) =>
        ({
          data: {
            data: roles.slice((page - 1) * pageSize, page * pageSize),
            total: roles.length,
          },
        }) as never,
    );

    const snapshot = await loadTopology(cluster);
    const serviceNode = snapshot.nodes.find((node) => node.kind === 'service');
    expect(serviceNode?.instances).toHaveLength(103);
    expect(serviceNode?.instances[0].ports).toContain('8030');
    expect(
      new Set(
        snapshot.nodes
          .filter((node) => node.kind === 'host')
          .map((node) => node.resourceId),
      ).size,
    ).toBe(105);
    expect(listClusterHosts).toHaveBeenCalledTimes(2);
    expect(listServiceRoleInstances).toHaveBeenCalledTimes(2);
    expect(listAllK8sInstances).not.toHaveBeenCalled();
    expect(
      snapshot.edges.filter((edge) => edge.kind === 'deployment'),
    ).toHaveLength(0);
    expect(
      snapshot.nodes.filter((node) => node.source === 'schematic'),
    ).toHaveLength(0);
    expect(
      snapshot.edges.filter((edge) => edge.kind === 'schematic'),
    ).toHaveLength(0);
  });

  it('filters foreign clusters and wrong-service roles without duplicating hosts', async () => {
    vi.mocked(listClusterHosts).mockResolvedValue({
      data: { records: [host(1), host(2, 8)], total: 2 },
    });
    vi.mocked(listClusterServices).mockResolvedValue({
      data: [
        service(1, 'DORIS'),
        service(2, 'MYSQL'),
        service(3, 'FOREIGN', 8),
      ],
    } as never);
    vi.mocked(listServiceRoleInstances).mockImplementation(
      async (_, id) =>
        ({
          data: {
            data: [
              role(id, id),
              { ...role(99, id), clusterId: 8 },
              role(100, 999),
            ],
            total: 3,
          },
        }) as never,
    );

    const snapshot = await loadTopology(cluster);
    const services = snapshot.nodes.filter((node) => node.kind === 'service');
    const hosts = snapshot.nodes.filter((node) => node.kind === 'host');
    expect(services.map((node) => node.label)).toEqual(['DORIS', 'MYSQL']);
    expect(services.every((node) => node.instances.length === 1)).toBe(true);
    expect(hosts).toHaveLength(1);
    expect(new Set(hosts.map((node) => node.resourceId)).size).toBe(1);
    expect(hosts.map((node) => node.zone)).toEqual(['other']);
    expect(services.every((node) => node.parentId === hosts[0].id)).toBe(true);
    expect(
      snapshot.edges.filter((edge) => edge.kind === 'deployment'),
    ).toHaveLength(0);
    expect(snapshot.warnings.join(' ')).toMatch(/集群|归属/);
  });

  it('reports unavailable role details without inventing a parent server', async () => {
    vi.mocked(listClusterServices).mockResolvedValue({
      data: [service(1, 'DORIS')],
    } as never);
    vi.mocked(listServiceRoleInstances).mockRejectedValue(new Error('offline'));
    const snapshot = await loadTopology(cluster);
    expect(snapshot.nodes.some((node) => node.kind === 'service')).toBe(false);
    expect(snapshot.warnings.length).toBeGreaterThan(0);
    expect(snapshot.warnings.join(' ')).toContain('DORIS');
    expect(snapshot.edges.some((edge) => edge.kind === 'deployment')).toBe(
      false,
    );
  });

  it('does not treat an installation state as live role health when no roles exist', async () => {
    vi.mocked(listClusterServices).mockResolvedValue({
      data: [{ ...service(1, 'DORIS'), serviceStateCode: 2 }],
    } as never);
    const snapshot = await loadTopology(cluster);
    expect(snapshot.nodes.some((node) => node.kind === 'service')).toBe(false);
    expect(snapshot.warnings.length).toBeGreaterThan(0);
  });

  it('loads Kubernetes Nodes by name, preserving Ready state and ignoring physical categories', async () => {
    vi.mocked(listClusterHosts).mockResolvedValue({
      data: {
        records: [
          { ...host(0), hostname: 'control', nodeLabel: 'control-plane' },
          { ...host(0), hostname: 'worker', nodeLabel: 'worker', hostState: 2 },
        ],
        total: 2,
      },
    });
    const snapshot = await loadTopology({ ...cluster, archType: 'k8s' });
    expect(listClusterHosts).toHaveBeenCalledWith(7, {
      page: 1,
      pageSize: 100,
    });
    expect(
      snapshot.nodes.map(({ id, zone, state }) => ({ id, zone, state })),
    ).toEqual([
      { id: 'host:7:name:control', zone: 'k8s', state: 'running' },
      { id: 'host:7:name:worker', zone: 'k8s', state: 'warning' },
    ]);
    expect(snapshot.nodes[1].details).toContainEqual({
      label: '节点状态',
      value: 'NotReady',
    });
    expect(listK8sResources).not.toHaveBeenCalled();
  });

  it('rejects unavailable Kubernetes Nodes instead of reporting an empty cluster', async () => {
    vi.mocked(listClusterHosts).mockRejectedValue(
      new Error('node access denied'),
    );
    await expect(loadTopology({ ...cluster, archType: 'k8s' })).rejects.toThrow(
      'node access denied',
    );
  });

  it('hides physical hosts labeled kubernetes and reports the Service / CR source boundary', async () => {
    vi.mocked(listClusterHosts).mockResolvedValue({
      data: { records: [{ ...host(1), nodeLabel: 'kubernetes' }], total: 1 },
    });
    const snapshot = await loadTopology(cluster);
    expect(snapshot.nodes).toEqual([]);
    expect(snapshot.warnings.join(' ')).toContain('Service / CR');
  });

  it('rejects critical source failures and non-progressing pagination', async () => {
    vi.mocked(listClusterServices).mockRejectedValue(new Error('offline'));
    await expect(loadTopology(cluster)).rejects.toThrow();
    vi.mocked(listClusterServices).mockResolvedValue({ data: [] });
    vi.mocked(listClusterHosts).mockResolvedValue({
      data: { records: [host(1)], total: 2 },
    });
    await expect(loadTopology(cluster)).rejects.toThrow(/分页|重复/);
    expect(listClusterHosts).toHaveBeenCalledTimes(3);
  });

  it('reports an early empty role page as incomplete instead of a successful empty service', async () => {
    vi.mocked(listClusterServices).mockResolvedValue({
      data: [service(1, 'DORIS')],
    } as never);
    vi.mocked(listServiceRoleInstances).mockResolvedValue({
      data: { data: [], total: 103 },
    } as never);
    const snapshot = await loadTopology(cluster);
    expect(snapshot.warnings.join(' ')).toMatch(/分页|不完整/);
    expect(snapshot.nodes.some((node) => node.kind === 'service')).toBe(false);
    expect(snapshot.warnings.length).toBeGreaterThan(0);
  });

  it('shows actual Kubernetes Services and CRs without querying Pods', async () => {
    vi.mocked(listAllK8sInstances).mockResolvedValue({
      data: [
        {
          id: 1,
          clusterId: 7,
          serviceName: 'FLINK',
          namespace: 'apps',
          state: 1,
          sourceKind: 'HELM',
        },
        {
          id: 2,
          clusterId: 7,
          serviceName: 'DORIS',
          namespace: 'apps',
          state: 1,
          sourceKind: 'CR',
        },
        {
          id: 3,
          clusterId: 8,
          serviceName: 'FOREIGN',
          namespace: 'apps',
          state: 1,
        },
      ],
    } as never);
    vi.mocked(listK8sResources).mockImplementation(
      async (_, __, type) =>
        ({
          data:
            type === 'pod'
              ? [
                  {
                    name: 'flink-0',
                    namespace: 'apps',
                    nodeName: 'worker-unregistered',
                    status: 'Running',
                    ready: '1/1',
                    podIP: '192.0.2.1',
                  },
                ]
              : [
                  {
                    name: 'flink-rest',
                    namespace: 'apps',
                    clusterIP: '10.43.0.1',
                    ports: [
                      {
                        name: 'rest',
                        port: 8081,
                        targetPort: 'rest-port',
                        nodePort: 30081,
                        protocol: 'TCP',
                      },
                    ],
                  },
                ],
        }) as never,
    );

    const snapshot = await loadTopology({ ...cluster, archType: 'k8s' });
    expect(listClusterServices).not.toHaveBeenCalled();
    expect(listServiceRoleInstances).not.toHaveBeenCalled();
    expect(listK8sResources).toHaveBeenCalledTimes(1);
    expect(listK8sResources).toHaveBeenCalledWith(7, 1, 'service');
    const flink = snapshot.nodes.find((node) => node.serviceName === 'FLINK');
    const doris = snapshot.nodes.find((node) => node.serviceName === 'DORIS');
    const worker = snapshot.nodes.find(
      (node) => node.label === 'worker-unregistered',
    );
    expect(flink?.label).toBe('flink-rest');
    expect(flink?.zone).toBe('k8s');
    expect(worker).toBeUndefined();
    expect(worker?.address).toBeUndefined();
    expect(flink?.details.map((detail) => detail.value).join(' ')).toContain(
      '8081/TCP',
    );
    expect(flink?.details.map((detail) => detail.value).join(' ')).toContain(
      '30081',
    );
    expect(doris?.instances).toEqual([]);
    expect(
      snapshot.edges.filter((edge) => edge.kind === 'deployment'),
    ).toHaveLength(0);
    expect(doris?.zone).toBe('k8s');
    expect(doris?.subtitle).toContain('CR');
    expect(snapshot.nodes.some((node) => node.label === 'FOREIGN')).toBe(false);
  });

  it('reports unavailable Kubernetes Services instead of inventing resources', async () => {
    vi.mocked(listAllK8sInstances).mockResolvedValue({
      data: [
        {
          id: 1,
          clusterId: 7,
          serviceName: 'FLINK',
          namespace: 'apps',
          state: 1,
        },
      ],
    } as never);
    vi.mocked(listK8sResources).mockImplementation(async (_, __, type) => {
      if (type === 'service') throw new Error('unavailable');
      return {
        data: [
          {
            name: 'flink-0',
            namespace: 'apps',
            nodeName: 'node-1',
            status: 'Running',
            ready: '1/1',
          },
        ],
      } as never;
    });
    const snapshot = await loadTopology({ ...cluster, archType: 'k8s' });
    expect(
      snapshot.edges.filter((edge) => edge.kind === 'deployment'),
    ).toHaveLength(0);
    expect(snapshot.nodes.filter((node) => node.kind === 'service')).toEqual(
      [],
    );
    expect(snapshot.nodes.filter((node) => node.kind === 'host')).toHaveLength(
      1,
    );
    expect(snapshot.warnings.join(' ')).toContain('Service');
  });

  it('rejects invalid cluster identifiers before making requests', async () => {
    await expect(
      loadTopology({ ...cluster, id: Number.NaN }),
    ).rejects.toThrow();
    expect(listClusterHosts).not.toHaveBeenCalled();
  });
});
