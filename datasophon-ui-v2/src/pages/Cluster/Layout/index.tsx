import {
  AlertOutlined,
  ClusterOutlined,
  DashboardOutlined,
  DesktopOutlined,
  FundProjectionScreenOutlined,
  HistoryOutlined,
  ImportOutlined,
  NodeIndexOutlined,
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history, Outlet, useIntl, useLocation, useParams } from '@umijs/max';
import { Badge, Button, Dropdown, Layout, Menu, Spin, Tag } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import { listClusters } from '@/services/cluster';
import { listAllK8sInstances } from '@/services/k8s';
import { listClusterServices } from '@/services/service';
import AddServiceModal from '../AddService/AddServiceModal';
import UploadManifestModal from '../Deploy/UploadManifestModal';
import UploadPackageModal from '../Deploy/UploadPackageModal';
import useStyles from './style';

const { Sider, Content } = Layout;

const CATALOG_LABEL: Record<string, string> = {
  ENVIRONMENT: '基础组件',
  MIDDLEWARE: '中间件',
  APPLICATION: '应用',
};

/** 侧边栏服务分组顺序，物理集群与 K8s 集群共用 */
const CATALOG_ORDER = ['ENVIRONMENT', 'MIDDLEWARE', 'APPLICATION'];

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

const K8sInstanceMenuItem: React.FC<K8sInstanceMenuItemProps> = ({
  instance,
}) => {
  // 轻对账发现 release 已从集群消失时，状态色让位给失联提示
  const badgeStatus = instance.missing
    ? 'error'
    : (K8S_STATE_BADGE_COLOR[instance.state] ?? 'default');
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
        title={
          instance.missing
            ? '该服务的 Helm release 已不在集群中，登记仍保留'
            : undefined
        }
      >
        {instance.serviceName}
        {instance.missing ? '（已失联）' : ''}
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
  const { styles } = useStyles();
  const intl = useIntl();
  const location = useLocation();
  const { clusterId } = useParams<{ clusterId: string }>();
  const numericClusterId = Number(clusterId);

  // ── 集群基本信息（挂载时一次性获取）────────────────────────
  const [clusterInfo, setClusterInfo] = useState<DATASOPHON.ClusterInfo | null>(
    null,
  );
  const [clusterLoading, setClusterLoading] = useState(true);

  // ── 部署/添加服务弹窗可见状态 ─────────────────────────────
  const [manifestModalOpen, setManifestModalOpen] = useState(false);
  const [packageModalOpen, setPackageModalOpen] = useState(false);
  const [addServiceModalOpen, setAddServiceModalOpen] = useState(false);

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

  // ── K8s 集群没有集群看板页，落到 /overview 时兜底回主机管理 ────────
  useEffect(() => {
    if (
      clusterInfo?.archType === 'k8s' &&
      location.pathname === `/cluster/${numericClusterId}/overview`
    ) {
      history.replace(`/cluster/${numericClusterId}/host`);
    }
  }, [clusterInfo?.archType, location.pathname, numericClusterId]);

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

  // ── K8s 集群：实例列表轮询（3 秒间隔，单次请求覆盖全部 namespace）──
  const [k8sInstances, setK8sInstances] = useState<
    DATASOPHON.K8sServiceInstanceVO[]
  >([]);

  useEffect(() => {
    if (clusterInfo?.archType !== 'k8s') return;

    let cancelled = false;
    const fetchK8s = async () => {
      try {
        const res = await listAllK8sInstances(numericClusterId);
        if (cancelled) return;
        setK8sInstances(Array.isArray(res) ? res : (res.data ?? []));
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

    // 仅物理集群有集群看板；K8s 集群的落地页/首位菜单仍是「监控概览」（K8sDashboard）。
    const overviewItem = {
      key: `/cluster/${numericClusterId}/overview`,
      icon: <DashboardOutlined />,
      label: intl.formatMessage({
        id: 'menu.cluster-overview',
        defaultMessage: '集群看板',
      }),
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
        key: `/cluster/${numericClusterId}/alarm`,
        icon: <AlertOutlined />,
        label: '告警管理',
      },
      {
        key: `/cluster/${numericClusterId}/command`,
        icon: <HistoryOutlined />,
        label: '命令历史',
      },
      {
        key: `/cluster/${numericClusterId}/observability-collector`,
        icon: <FundProjectionScreenOutlined />,
        label: intl.formatMessage({
          id: 'menu.observability-collector',
          defaultMessage: '链路跟踪',
        }),
      },
      {
        key: `/cluster/${numericClusterId}/lineage`,
        icon: <NodeIndexOutlined />,
        label: intl.formatMessage({
          id: 'menu.lineage',
          defaultMessage: '数据血缘',
        }),
      },
      {
        key: 'system-center',
        icon: <SettingOutlined />,
        label: '系统中心',
        disabled: true,
      },
    ];

    if (clusterInfo?.archType === 'k8s') {
      // K8s：与物理集群一致按服务分类（catalog）分组，namespace 降为实例属性
      // catalog 落到未知值（含 null——如对应 frame 行被清理导致 LEFT JOIN 落空）的
      // 实例兜底进「其他」分组，保证「所有实例都会被渲染」这一不变量（D2）；
      // 否则这些实例在侧边栏彻底隐身，而「取消接管」按钮只在实例详情页里，
      // 导致登记记录无法从 UI 清理。
      const catalogGroups = [
        ...CATALOG_ORDER.map((catalog) => ({
          key: `cat-${catalog}`,
          label: CATALOG_LABEL[catalog] || catalog,
          instances: k8sInstances.filter((inst) => inst.catalog === catalog),
        })),
        {
          key: 'cat-OTHER',
          label: '其他',
          instances: k8sInstances.filter(
            (inst) => !CATALOG_ORDER.includes(inst.catalog),
          ),
        },
      ];
      const catItems = catalogGroups.flatMap(({ key, label, instances }) =>
        instances.length
          ? [
              {
                key,
                label,
                children: instances.map((inst) => ({
            key: `/cluster/${numericClusterId}/service/${inst.id}`,
            label: <K8sInstanceMenuItem instance={inst} />,
                })),
              },
            ]
          : [],
      );
      // 接管集群多一个「接管服务」入口，兼作 D13 的「重新扫描」入口
      const takeoverItem =
        clusterInfo?.manageMode === 'IMPORTED'
          ? [
              {
                key: `/cluster/${numericClusterId}/takeover`,
                icon: <ImportOutlined />,
                label: '接管服务',
              },
            ]
          : [];
      return [
        baseItem,
        {
          key: `/cluster/${numericClusterId}/k8s-dashboard`,
          icon: <FundProjectionScreenOutlined />,
          label: '监控概览',
        },
        ...takeoverItem,
        ...catItems,
        ...bottomItems,
      ];
    }

    // 物理集群：按 catalog 分组（原有逻辑不变）
    const items: any[] = [overviewItem, baseItem];
    for (const cat of CATALOG_ORDER) {
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
    clusterInfo?.manageMode,
    groupedServices,
    k8sInstances,
    numericClusterId,
    intl,
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

  const currentPath = history.location.pathname.replace(/^\/ddh(?=\/|$)/, '');

  return (
    <ClusterContext.Provider
      value={{ clusterId: numericClusterId, clusterInfo }}
    >
      {clusterInfo.archType !== 'k8s' && (
        <>
          <UploadManifestModal
            open={manifestModalOpen}
            onClose={() => setManifestModalOpen(false)}
          />
          <UploadPackageModal
            open={packageModalOpen}
            onClose={() => setPackageModalOpen(false)}
          />
          <AddServiceModal
            open={addServiceModalOpen}
            onClose={() => setAddServiceModalOpen(false)}
          />
        </>
      )}
      <PageContainer
        pageHeaderRender={false}
        childrenContentStyle={{ padding: 0 }}
      >
        <Layout className={styles.pageLayout}>
          <Sider width={216} theme="dark" className={styles.sider}>
            <div className={styles.siderBody}>
              <div className={styles.siderHeader}>
                <span className={styles.siderEyebrow}>集群</span>
                <span className={styles.siderClusterName}>
                  {clusterInfo.clusterName}
                </span>
              </div>
              <Menu
                theme="dark"
                mode="inline"
                className={styles.menu}
                selectedKeys={[currentPath]}
                items={menuItems}
                onClick={({ key }) => {
                  if (!key.startsWith('/')) return;
                  history.push(key);
                }}
              />
            </div>
          </Sider>
          <Content className={styles.content}>
            <div className={styles.contentInner}>
              <div className={styles.clusterBar}>
                <div className={styles.clusterIdentity}>
                  <span className={styles.breadcrumb}>集群管理 / 当前集群</span>
                  <div className={styles.clusterNameRow}>
                    <span className={styles.clusterName}>
                      {clusterInfo.clusterName}
                    </span>
                    <Tag
                      color={
                        STATE_BADGE_COLOR[clusterInfo.clusterStateCode ?? 0] ??
                        'default'
                      }
                      variant="filled"
                    >
                      {clusterInfo.clusterState ?? '状态未知'}
                    </Tag>
                  </div>
                </div>
                {clusterInfo.archType !== 'k8s' && (
                  <div className={styles.clusterActions}>
                    <Dropdown
                      menu={{
                        items: [
                          {
                            key: 'manifest',
                            label: '部署清单',
                            onClick: () => setManifestModalOpen(true),
                          },
                          {
                            key: 'package',
                            label: '部署包',
                            onClick: () => setPackageModalOpen(true),
                          },
                        ],
                      }}
                    >
                      <Button icon={<UploadOutlined />}>上传部署</Button>
                    </Dropdown>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => setAddServiceModalOpen(true)}
                    >
                      添加服务
                    </Button>
                  </div>
                )}
              </div>
              <div className={styles.outlet}>
                <Outlet />
              </div>
            </div>
          </Content>
        </Layout>
      </PageContainer>
    </ClusterContext.Provider>
  );
};

export default ClusterLayout;
