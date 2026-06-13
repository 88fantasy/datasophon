import { history, useParams } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect, useState } from 'react';
import { listClusterServices } from '@/services/service';

const ServiceManage: React.FC = () => {
  const { clusterId } = useParams<{ clusterId: string }>();
  const numericClusterId = Number(clusterId);
  const [redirected, setRedirected] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listClusterServices(numericClusterId)
      .then((res) => {
        if (cancelled) return;
        const list = Array.isArray(res) ? res : (res.data ?? []);
        if (list.length > 0) {
          history.replace(`/cluster/${numericClusterId}/service/${list[0].id}`);
        }
        setRedirected(true);
      })
      .catch(() => {
        if (!cancelled) setRedirected(true);
      });
    return () => {
      cancelled = true;
    };
  }, [numericClusterId]);

  if (!redirected) {
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

  return <div>当前集群暂无服务实例</div>;
};

export default ServiceManage;
