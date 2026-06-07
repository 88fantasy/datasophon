import { history, useParams } from '@umijs/max';
import { Button, Dropdown, Spin, Tabs } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getServiceInstance,
  getServiceWebUis,
} from '@/services/datasophon/service';
import InstanceTab from './Instance';

const ServiceInstance: React.FC = () => {
  const { clusterId, instanceId } = useParams<{
    clusterId: string;
    instanceId: string;
  }>();
  const numericClusterId = Number(clusterId);
  const numericInstanceId = Number(instanceId);

  const [serviceInfo, setServiceInfo] =
    useState<DATASOPHON.ServiceInstanceInfo | null>(null);
  const [webUis, setWebUis] = useState<DATASOPHON.WebuiInfo[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      try {
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
  }, [numericClusterId, numericInstanceId]);

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
      <Tabs.TabPane tab="配置" key="setting" disabled>
        {/* 将在子切片 4b 实现 */}
      </Tabs.TabPane>
    </Tabs>
  );
};

export default ServiceInstance;
