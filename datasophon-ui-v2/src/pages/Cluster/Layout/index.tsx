import {
  AlertOutlined,
  ClusterOutlined,
  DesktopOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history, Outlet, useParams } from '@umijs/max';
import { Badge, Layout, Menu, Spin, Tag } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import { listClusters } from '@/services/datasophon/cluster';
import {
  listClusterServices,
  listK8sInstances,
  listK8sNamespaces,
} from '@/services/datasophon/service';

const { Sider, Content } = Layout;

const CATALOG_LABEL: Record<string, string> = {
  ENVIRONMENT: '基础组件',
  MIDDLEWARE: '中间件',
  APPLICATION: '应用',
};

const STATE_BADGE_COLOR: Record<
  number,
  'success' | 'error' | 'warning' | 'default'
> = {
  1: 'default',
  2: 'success',
  3: 'warning',
  4: 'error',
};

/** K8s 实例状态：0 初始化 / 1 成功 / 2 失败 */
const K8S_STATE_BADGE_COLOR: Record<number, 'success' | 'error' | 'default'> = {
  0: 'default',
  1: 'success',
  2: 'error',
};

interface K8sInstanceMenuItemProps {
  instance: DATASOPHON.K8sServiceInstanceVO;
}

const K8sInstanceMenuItem: React.FC<K8sInstanceMenuItemProps> = ({ instance }) => {
  const badgeStatus = K8S_STATE_BADGE_COLOR[instance.state] ?? 'default';
  return (
    <div style={{ display: 'flex', alignItems: 'center', width: '100%' }}>
      <Badge status={badgeStatus} />
      <span
        style={{
          marginLeft: 8,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {instance.serviceName}
      </span>
    </div>
  );
};

interface ServiceMenuItemProps {
  service: DATASOPHON.ServiceInstanceInfo;
}

const ServiceMenuItem: React.FC<ServiceMenuItemProps> = ({ service }) => {
  const badgeStatus = STATE_BADGE_COLOR[service.serviceStateCode] ?? 'default';
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        width: '100%',
      }}
    >
      <span
        style={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          flex: 1,
        }}
      >
        <Badge status={badgeStatus} />
        <span style={{ marginLeft: 8 }}>
          {service.label || service.serviceName}
        </span>
      </span>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          marginLeft: 8,
          flexShrink: 0,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {service.alertNum > 0 && (
          <Tag
            color="orange"
            style={{
              margin: 0,
              fontSize: 10,
              lineHeight: '16px',
              padding: '0 4px',
            }}
          >
            {service.alertNum}
          </Tag>
        )}
        {service.needRestart && (
          <ReloadOutlined style={{ color: '#999', fontSize: 11 }} />
        )}
      </span>
    </div>
  );
};

const ClusterLayout: React.FC = () => {
  const { clusterId } = useParams<{ clusterId: string }>();
  const numericClusterId = Number(clusterId);

  // ── 集群基本信息（挂载时一次性获取）────────────────────────
  const [clusterInfo, setClusterInfo] = useState<DATASOPHON.ClusterInfo | null>(
    null,
  );
  const [clusterLoading, setClusterLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setClusterLoading(true);
    listClusters()
      .then((res) => {
        if (cancelled) return;
        const list = Array.isArray(res) ? res : (res.data ?? []);
        setClusterInfo(list.find((c) => c.id === numericClusterId) ?? null);
      })
      .catch(() => {
        /* global error handler */
      })
      .finally(() => {
        if (!cancelled) setClusterLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [numericClusterId]);

  // ── 物理集群：服务列表轮询（3 秒间隔）────────────────────────────
  const [serviceList, setServiceList] = useState<
    DATASOPHON.ServiceInstanceInfo[]
  >([]);

  useEffect(() => {
    if (clusterInfo?.archType === 'k8s') return; // K8s 由独立 effect 处理

    let cancelled = false;
    const fetchServices = async () => {
      try {
        const res = await listClusterServices(numericClusterId);
        if (!cancelled) {
          setServiceList(Array.isArray(res) ? res : (res.data ?? []));
        }
      } catch {
        /* global error handler */
      }
    };

    fetchServices();
    const timer = setInterval(fetchServices, 3000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [numericClusterId, clusterInfo?.archType]);

  // ── K8s 集群：namespace + 实例列表轮询（3 秒间隔）──────────────
  const [k8sNamespaces, setK8sNamespaces] = useState<DATASOPHON.K8sNamespace[]>([]);
  const [k8sInstancesMap, setK8sInstancesMap] = useState<
    Record<string, DATASOPHON.K8sServiceInstanceVO[]>
  >({});

  useEffect(() => {
    if (clusterInfo?.archType !== 'k8s') return;

    let cancelled = false;
    const fetchK8s = async () => {
      try {
        const nsRes = await listK8sNamespaces(numericClusterId);
        const namespaces = Array.isArray(nsRes) ? nsRes : (nsRes.data ?? []);
        if (cancelled) return;
        setK8sNamespaces(namespaces);

        const map: Record<string, DATASOPHON.K8sServiceInstanceVO[]> = {};
        await Promise.all(
          namespaces.map(async (ns) => {
            const iRes = await listK8sInstances(numericClusterId, ns.namespace);
            map[ns.namespace] = Array.isArray(iRes) ? iRes : (iRes.data ?? []);
          }),
        );
        if (!cancelled) setK8sInstancesMap(map);
      } catch {
        /* global error handler */
      }
    };

    fetchK8s();
    const timer = setInterval(fetchK8s, 3000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [numericClusterId, clusterInfo?.archType]);

  // ── 按 catalog 分组 ────────────────────────────────────
  const groupedServices = useMemo(() => {
    const groups: Record<string, DATASOPHON.ServiceInstanceInfo[]> = {};
    for (const svc of serviceList) {
      const cat = svc.catalog || 'OTHER';
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(svc);
    }
    return groups;
  }, [serviceList]);

  // ── 菜单项 ────────────────────────────────────────────
  const menuItems = useMemo(() => {
    const baseItem = {
      key: `/cluster/${numericClusterId}/host`,
      icon: <DesktopOutlined />,
      label: '主机管理',
    };

    const bottomItems = [
      { type: 'divider' as const },
      {
        key: 'service-manage',
        icon: <ClusterOutlined />,
        label: '服务管理',
        disabled: true,
      },
      {
        key: 'alarm-manage',
        icon: <AlertOutlined />,
        label: '告警管理',
        disabled: true,
      },
      {
        key: 'system-center',
        icon: <SettingOutlined />,
        label: '系统中心',
        disabled: true,
      },
    ];

    if (clusterInfo?.archType === 'k8s') {
      // K8s：两层菜单 namespace → 实例
      const nsItems = k8sNamespaces.map((ns) => {
        const instances = k8sInstancesMap[ns.namespace] ?? [];
        return {
          key: `ns-${ns.namespace}`,
          label: ns.namespace,
          children: instances.map((inst) => ({
            key: `/cluster/${numericClusterId}/service/${inst.id}`,
            label: <K8sInstanceMenuItem instance={inst} />,
          })),
        };
      });
      return [baseItem, ...nsItems, ...bottomItems];
    }

    // 物理集群：按 catalog 分组（原有逻辑不变）
    const items: any[] = [baseItem];
    const catalogOrder = ['ENVIRONMENT', 'MIDDLEWARE', 'APPLICATION'];
    for (const cat of catalogOrder) {
      const services = groupedServices[cat];
      if (!services?.length) continue;
      items.push({
        key: `cat-${cat}`,
        label: CATALOG_LABEL[cat] || cat,
        children: services.map((s) => ({
          key: `/cluster/${numericClusterId}/service/${s.id}`,
          label: <ServiceMenuItem service={s} />,
        })),
      });
    }
    return [...items, ...bottomItems];
  }, [
    clusterInfo?.archType,
    groupedServices,
    k8sNamespaces,
    k8sInstancesMap,
    numericClusterId,
  ]);

  // ── 渲染 ──────────────────────────────────────────────
  if (clusterLoading) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          padding: 120,
        }}
      >
        <Spin size="large" />
      </div>
    );
  }

  if (!clusterInfo) {
    return (
      <PageContainer title="集群不存在">
        <p>未找到集群（ID: {clusterId}），请返回集群列表重新选择。</p>
      </PageContainer>
    );
  }

  const currentPath = history.location.pathname;

  return (
    <ClusterContext.Provider
      value={{ clusterId: numericClusterId, clusterInfo }}
    >
      <Layout style={{ minHeight: 'calc(100vh - 56px)' }}>
        <Sider
          width={200}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}
        >
          <div
            style={{
              padding: '16px',
              fontWeight: 600,
              borderBottom: '1px solid #f0f0f0',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {clusterInfo.clusterName}
          </div>
          <Menu
            mode="inline"
            selectedKeys={[currentPath]}
            items={menuItems}
            onClick={({ key }) => {
              if (!key.startsWith('/')) return;
              history.push(key);
            }}
          />
        </Sider>
        <Content style={{ padding: 16, background: '#f5f5f5' }}>
          <Outlet />
        </Content>
      </Layout>
    </ClusterContext.Provider>
  );
};

export default ClusterLayout;
