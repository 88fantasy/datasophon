import { history, useParams } from '@umijs/max';
import { Button, Dropdown, Spin, Tabs } from 'antd';
import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { RESOURCE_TYPE_LABELS } from '@/constants/resourceType';
import ClusterContext from '@/context/ClusterContext';
import {
  getServiceInstance,
  getServiceWebUis,
  listK8sResourceTypes,
} from '@/services/datasophon/service';
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

  const tabBarExtraContent = useMemo(() => {
    if (!webUis?.length) return undefined;
    const items = webUis.map((val) => ({
      key: val.name,
      label: val.name,
      onClick: () => window.open(val.webUrl),
    }));
    return {
      right: (
        <Dropdown menu={{ items }}>
          <Button variant="filled" color="default">
            WebUI
          </Button>
        </Dropdown>
      ),
    };
  }, [webUis]);

  const onTabChange = useCallback(
    (key: string) => {
      const base = `/cluster/${numericClusterId}/service/${numericInstanceId}`;
      switch (key) {
        case 'overview':
          history.push(`${base}/overview`);
          break;
        case 'instance':
          history.push(`${base}/instance`);
          break;
        case 'setting':
          history.push(`${base}/setting`);
          break;
        case 'queue':
          history.push(`${base}/queue`);
          break;
      }
    },
    [numericClusterId, numericInstanceId],
  );

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 120 }}>
        <Spin size="large" />
      </div>
    );
  }

  // ── K8s 实例页：资源 Tab（动态）+ 配置 Tab ─────────────────────────
  if (isK8s) {
    return (
      <Tabs defaultActiveKey={k8sResourceTypes[0] ?? 'setting'}>
        {k8sResourceTypes.map((rt) => (
          <Tabs.TabPane tab={RESOURCE_TYPE_LABELS[rt] ?? rt} key={rt}>
            <K8sResource
              clusterId={numericClusterId}
              instanceId={numericInstanceId}
              resourceType={rt}
            />
          </Tabs.TabPane>
        ))}
        <Tabs.TabPane tab="配置" key="setting">
          <SettingTab
            clusterId={numericClusterId}
            instanceId={numericInstanceId}
          />
        </Tabs.TabPane>
      </Tabs>
    );
  }

  // ── 物理集群实例页（原有逻辑不变）─────────────────────────────────
  return (
    <Tabs
      tabBarExtraContent={tabBarExtraContent}
      defaultActiveKey="instance"
      onChange={onTabChange}
    >
      {serviceInfo?.dashboardUrl && (
        <Tabs.TabPane tab="概览" key="overview">
          <iframe
            className="w-full"
            style={{ height: '72vh', border: 'none' }}
            src={serviceInfo.dashboardUrl}
            title="概览"
          />
        </Tabs.TabPane>
      )}
      <Tabs.TabPane tab="实例" key="instance">
        <InstanceTab
          clusterId={numericClusterId}
          instanceId={numericInstanceId}
        />
      </Tabs.TabPane>
      <Tabs.TabPane tab="配置" key="setting">
        <SettingTab
          clusterId={numericClusterId}
          instanceId={numericInstanceId}
        />
      </Tabs.TabPane>
      {serviceInfo?.serviceName === 'YARN' && (
        <Tabs.TabPane tab="资源配置" key="queue">
          <QueueTab clusterId={numericClusterId} />
        </Tabs.TabPane>
      )}
    </Tabs>
  );
};

export default ServiceInstance;
