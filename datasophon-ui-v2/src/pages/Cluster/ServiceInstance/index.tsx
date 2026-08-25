import { history, useIntl, useParams } from '@umijs/max';
import type { TabsProps } from 'antd';
import { Button, Dropdown, message, Popconfirm, Space, Spin, Tabs } from 'antd';
import React, { useContext, useEffect, useMemo, useState } from 'react';
import { RESOURCE_TYPE_LABELS } from '@/constants/resourceType';
import ClusterContext from '@/context/ClusterContext';
import MonitorTab from '@/pages/Cluster/ObservabilityCollector/MonitorTab';
import ApisixDashboard from '@/pages/monitor/ApisixMonitor';
import DSDashboard from '@/pages/monitor/DolphinSchedulerMonitor';
import DorisDashboard from '@/pages/monitor/DorisMonitor';
import GravitinoDashboard from '@/pages/monitor/GravitinoMonitor';
import JuiceFSDashboard from '@/pages/monitor/JuiceFSMonitor';
import KyuubiDashboard from '@/pages/monitor/KyuubiMonitor';
import NacosDashboard from '@/pages/monitor/NacosMonitor';
import ValkeyDashboard from '@/pages/monitor/ValkeyMonitor';
import ZooKeeperDashboard from '@/pages/monitor/ZooKeeperMonitor';
import {
  cancelTakeover,
  getK8sInstance,
  listK8sResourceTypes,
} from '@/services/k8s';
import {
  deleteServiceInstance,
  getServiceInstance,
  getServiceWebUis,
} from '@/services/service';
import ApisixGatewayPanel from './ApisixGateway';
import DsWorkflowPanel from './DsWorkflow';
import InstanceTab from './Instance';
import K8sResource from './K8sResource';
import QueueTab from './Queue';
import SettingTab from './Setting';
import ImportedValuesViewer from './Setting/ImportedValuesViewer';

/**
 * K8s 服务名 → 监控看板。
 *
 * key 取框架清单包里的服务名（= chart 名，见 package/raw/meta/datacluster-k8s/），
 * 与物理侧的大写服务名（APISIX / DORIS…）是两套命名，不能混用。
 * 未在此表中的服务不出监控 Tab——宁可没有，也不要给一个空看板。
 */
const K8S_MONITOR_DASHBOARDS: Record<
  string,
  React.FC<{
    clusterId: number;
    embedded?: boolean;
    job?: string;
    monitorProfile?: string;
  }>
> = {
  apisix: ApisixDashboard,
  'doris-disaggregated': DorisDashboard,
  'doris-coupled': DorisDashboard,
  'dolphinscheduler-helm': DSDashboard,
  'juicefs-csi-driver': JuiceFSDashboard,
  kyuubi: KyuubiDashboard,
  nacos: NacosDashboard,
  zookeeper: ZooKeeperDashboard,
};

const ServiceInstance: React.FC = () => {
  const intl = useIntl();
  const { clusterId, instanceId } = useParams<{
    clusterId: string;
    instanceId: string;
  }>();
  const numericClusterId = Number(clusterId);
  const numericInstanceId = Number(instanceId);

  const clusterCtx = useContext(ClusterContext);
  const isK8s = clusterCtx?.clusterInfo?.archType === 'k8s';

  // ── 物理集群状态 ───────────────────────────────────────
  const [serviceInfo, setServiceInfo] =
    useState<DATASOPHON.ServiceInstanceInfo | null>(null);
  const [webUis, setWebUis] = useState<DATASOPHON.WebuiInfo[]>([]);

  // ── K8s 状态 ──────────────────────────────────────────
  const [k8sResourceTypes, setK8sResourceTypes] = useState<string[]>([]);
  const [k8sInstance, setK8sInstance] =
    useState<DATASOPHON.K8sServiceInstanceVO | null>(null);

  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      try {
        if (isK8s) {
          const [typesRes, instanceRes] = await Promise.all([
            listK8sResourceTypes(numericClusterId, numericInstanceId),
            getK8sInstance(numericClusterId, numericInstanceId),
          ]);
          if (!cancelled) {
            setK8sResourceTypes(
              Array.isArray(typesRes)
                ? typesRes
                : ((typesRes as any).data ?? []),
            );
            setK8sInstance((instanceRes as any).data ?? null);
          }
        } else {
          const [infoRes, webuiRes] = await Promise.all([
            getServiceInstance(numericClusterId, numericInstanceId),
            getServiceWebUis(numericClusterId, numericInstanceId),
          ]);
          if (cancelled) return;
          const info = Array.isArray(infoRes)
            ? (infoRes as any)[0]
            : (infoRes as any).data;
          const webuiData = Array.isArray(webuiRes)
            ? (webuiRes as any)
            : ((webuiRes as any).data ?? []);
          setServiceInfo(info);
          setWebUis(webuiData);
        }
      } catch {
        /* global error handler */
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchData();
    return () => {
      cancelled = true;
    };
  }, [numericClusterId, numericInstanceId, isK8s]);

  // 接管的实例只能「取消接管」——删除会走到 helm uninstall，后端也已拦截
  const isImported = k8sInstance?.source === 'IMPORTED';

  const handleCancelTakeover = async () => {
    setDeleting(true);
    try {
      await cancelTakeover(numericClusterId, numericInstanceId);
      message.success('已取消接管，目标集群未做任何改动');
      history.replace(`/cluster/${numericClusterId}/service`);
    } finally {
      setDeleting(false);
    }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await deleteServiceInstance(numericClusterId, numericInstanceId);
      message.success('服务已删除');
      history.replace(`/cluster/${numericClusterId}/service`);
    } finally {
      setDeleting(false);
    }
  };

  const tabBarExtraContent = useMemo(() => {
    const webUiButton = webUis?.length ? (
      <Dropdown
        menu={{
          items: webUis.map((val) => ({
            key: val.name,
            label: val.name,
            onClick: () => window.open(val.webUrl),
          })),
        }}
      >
        <Button variant="filled" color="default">
          WebUI
        </Button>
      </Dropdown>
    ) : null;
    return {
      right: (
        <Space>
          {webUiButton}
          <Popconfirm
            title={`确认删除服务「${serviceInfo?.serviceName ?? ''}」？`}
            description="需先停止全部角色实例，删除后无法恢复"
            onConfirm={handleDelete}
          >
            <Button danger loading={deleting}>
              删除服务
            </Button>
          </Popconfirm>
        </Space>
      ),
    };
  }, [webUis, serviceInfo, deleting, numericClusterId, numericInstanceId]);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 120 }}>
        <Spin size="large" />
      </div>
    );
  }

  // ── K8s 实例页：资源 Tab（动态）+ 配置 Tab ─────────────────────────
  if (isK8s) {
    const K8sMonitorDashboard = k8sInstance?.serviceName
      ? K8S_MONITOR_DASHBOARDS[k8sInstance.serviceName.toLowerCase()]
      : undefined;
    const k8sItems: TabsProps['items'] = [
      ...(K8sMonitorDashboard
        ? [
            {
              key: 'monitor',
              label: '监控',
              children: (
                <K8sMonitorDashboard
                  clusterId={numericClusterId}
                  embedded
                  job={k8sInstance?.metricsJob}
                  monitorProfile={k8sInstance?.monitorProfile}
                />
              ),
            },
          ]
        : []),
      ...k8sResourceTypes.map((rt) => ({
        key: rt,
        label: RESOURCE_TYPE_LABELS[rt] ?? rt,
        children: (
          <K8sResource
            clusterId={numericClusterId}
            instanceId={numericInstanceId}
            resourceType={rt}
          />
        ),
      })),
      {
        key: 'setting',
        label: '配置',
        children: isImported ? (
          <ImportedValuesViewer
            clusterId={numericClusterId}
            instanceId={numericInstanceId}
            releaseName={k8sInstance?.releaseName}
          />
        ) : (
          <SettingTab
            clusterId={numericClusterId}
            instanceId={numericInstanceId}
          />
        ),
      },
    ];
    return (
      <Tabs
        defaultActiveKey={
          K8sMonitorDashboard ? 'monitor' : (k8sResourceTypes[0] ?? 'setting')
        }
        tabBarExtraContent={
          isImported
            ? {
                right: (
                  <Popconfirm
                    title="确认取消接管？"
                    description="仅移除平台的登记记录，目标集群里的服务不受任何影响"
                    onConfirm={handleCancelTakeover}
                  >
                    <Button danger loading={deleting}>
                      取消接管
                    </Button>
                  </Popconfirm>
                ),
              }
            : undefined
        }
        items={k8sItems}
      />
    );
  }

  // ── 物理集群实例页（原有逻辑不变）─────────────────────────────────
  const items: NonNullable<TabsProps['items']> = [];
  const isApisix = serviceInfo?.serviceName === 'APISIX';
  const isValkey = serviceInfo?.serviceName === 'VALKEY';
  const isDS = serviceInfo?.serviceName === 'DS';
  const isDoris = serviceInfo?.serviceName === 'DORIS';
  const isNacos = serviceInfo?.serviceName === 'NACOS';
  const isGravitino = serviceInfo?.serviceName === 'GRAVITINO';
  const isOtelCollector = serviceInfo?.serviceName === 'OTELCOLLECTOR';
  const hasPrimaryMonitor =
    isApisix ||
    isValkey ||
    isDS ||
    isDoris ||
    isNacos ||
    isGravitino ||
    isOtelCollector;
  if (hasPrimaryMonitor) {
    let primaryMonitor: React.ReactNode = null;
    if (isApisix) {
      primaryMonitor = (
        <ApisixDashboard clusterId={numericClusterId} embedded />
      );
    } else if (isValkey) {
      primaryMonitor = (
        <ValkeyDashboard clusterId={numericClusterId} embedded />
      );
    } else if (isDS) {
      primaryMonitor = <DSDashboard clusterId={numericClusterId} embedded />;
    } else if (isDoris) {
      primaryMonitor = <DorisDashboard clusterId={numericClusterId} embedded />;
    } else if (isNacos) {
      primaryMonitor = <NacosDashboard clusterId={numericClusterId} embedded />;
    } else if (isGravitino) {
      primaryMonitor = (
        <GravitinoDashboard clusterId={numericClusterId} embedded />
      );
    } else if (isOtelCollector) {
      primaryMonitor = <MonitorTab clusterId={numericClusterId} embedded />;
    }
    items.push({
      key: 'monitor',
      label: '监控',
      children: primaryMonitor,
    });
  }
  if (isApisix) {
    items.push({
      key: 'apisixGateway',
      label: '网关配置',
      children: (
        <ApisixGatewayPanel
          clusterId={numericClusterId}
          instanceId={numericInstanceId}
        />
      ),
    });
  }
  if (isDS) {
    items.push({
      key: 'dsWorkflow',
      label: intl.formatMessage({ id: 'dsWorkflow.tab' }),
      children: (
        <DsWorkflowPanel
          clusterId={numericClusterId}
          instanceId={numericInstanceId}
          dsWebUrl={webUis[0]?.webUrl}
        />
      ),
    });
  }
  if (serviceInfo?.dashboardUrl) {
    items.push({
      key: 'overview',
      label: '概览',
      children: (
        <iframe
          className="w-full"
          style={{ height: '72vh', border: 'none' }}
          src={serviceInfo.dashboardUrl}
          title="概览"
        />
      ),
    });
  }
  items.push({
    key: 'instance',
    label: '实例',
    children: (
      <InstanceTab
        clusterId={numericClusterId}
        instanceId={numericInstanceId}
      />
    ),
  });
  items.push({
    key: 'setting',
    label: '配置',
    children: (
      <SettingTab clusterId={numericClusterId} instanceId={numericInstanceId} />
    ),
  });
  if (serviceInfo?.serviceName === 'YARN') {
    items.push({
      key: 'queue',
      label: '资源配置',
      children: <QueueTab clusterId={numericClusterId} />,
    });
  }
  return (
    <Tabs
      key={`${numericInstanceId}-${serviceInfo?.serviceName ?? ''}`}
      tabBarExtraContent={tabBarExtraContent}
      defaultActiveKey={hasPrimaryMonitor ? 'monitor' : 'instance'}
      items={items}
    />
  );
};

export default ServiceInstance;
