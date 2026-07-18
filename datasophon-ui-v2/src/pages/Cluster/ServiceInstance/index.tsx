import { history, useParams } from '@umijs/max';
import type { TabsProps } from 'antd';
import { Button, Dropdown, message, Popconfirm, Space, Spin, Tabs } from 'antd';
import React, { useContext, useEffect, useMemo, useState } from 'react';
import { RESOURCE_TYPE_LABELS } from '@/constants/resourceType';
import ClusterContext from '@/context/ClusterContext';
import ApisixDashboard from '@/pages/monitor/ApisixMonitor';
import DorisDashboard from '@/pages/monitor/DorisMonitor';
import NacosDashboard from '@/pages/monitor/NacosMonitor';
import { listK8sResourceTypes } from '@/services/k8s';
import {
  deleteServiceInstance,
  getServiceInstance,
  getServiceWebUis,
} from '@/services/service';
import InstanceTab from './Instance';
import K8sResource from './K8sResource';
import QueueTab from './Queue';
import SettingTab from './Setting';

const ServiceInstance: React.FC = () => {
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

  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      try {
        if (isK8s) {
          const res = await listK8sResourceTypes(
            numericClusterId,
            numericInstanceId,
          );
          if (!cancelled) {
            setK8sResourceTypes(
              Array.isArray(res) ? res : ((res as any).data ?? []),
            );
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
    const k8sItems: TabsProps['items'] = [
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
        children: (
          <SettingTab
            clusterId={numericClusterId}
            instanceId={numericInstanceId}
          />
        ),
      },
    ];
    return (
      <Tabs
        defaultActiveKey={k8sResourceTypes[0] ?? 'setting'}
        items={k8sItems}
      />
    );
  }

  // ── 物理集群实例页（原有逻辑不变）─────────────────────────────────
  const items: NonNullable<TabsProps['items']> = [];
  const isApisix = serviceInfo?.serviceName === 'APISIX';
  if (isApisix) {
    items.push({
      key: 'monitor',
      label: '监控',
      children: <ApisixDashboard clusterId={numericClusterId} />,
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
  if (serviceInfo?.serviceName === 'DORIS') {
    items.push({
      key: 'monitor',
      label: '监控',
      children: <DorisDashboard clusterId={numericClusterId} embedded />,
    });
  }
  if (serviceInfo?.serviceName === 'NACOS') {
    items.push({
      key: 'monitor',
      label: '监控',
      children: <NacosDashboard clusterId={numericClusterId} />,
    });
  }

  return (
    <Tabs
      key={`${numericInstanceId}-${serviceInfo?.serviceName ?? ''}`}
      tabBarExtraContent={tabBarExtraContent}
      defaultActiveKey={isApisix ? 'monitor' : 'instance'}
      items={items}
    />
  );
};

export default ServiceInstance;
