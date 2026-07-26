import { useSearchParams } from '@umijs/max';
import { Tabs, Typography } from 'antd';
import React, { useContext, useEffect, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import GroupTab from './Group';
import HistoryTab from './History';
import MetricTab from './Metric';

const TAB_KEYS = new Set(['group', 'metric', 'history']);

const AlarmManage: React.FC = () => {
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab');
  const [activeTab, setActiveTab] = useState(
    initialTab && TAB_KEYS.has(initialTab) ? initialTab : 'group',
  );
  const [filterGroupId, setFilterGroupId] = useState<number | undefined>();

  useEffect(() => {
    const tab = searchParams.get('tab');
    setActiveTab(tab && TAB_KEYS.has(tab) ? tab : 'group');
  }, [searchParams]);

  const selectTab = (key: string) => {
    setActiveTab(key);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', key);
    setSearchParams(nextParams, { replace: true });
  };

  const handleViewMetrics = (groupId: number) => {
    setFilterGroupId(groupId);
    selectTab('metric');
  };

  const items = [
    {
      key: 'group',
      label: '告警组',
      children: (
        <GroupTab clusterId={clusterId} onViewMetrics={handleViewMetrics} />
      ),
    },
    {
      key: 'metric',
      label: '告警指标',
      children: (
        <MetricTab clusterId={clusterId} defaultGroupId={filterGroupId} />
      ),
    },
    {
      key: 'history',
      label: '告警历史',
      children: <HistoryTab clusterId={clusterId} />,
    },
  ];

  return (
    <div style={{ minHeight: '100%' }}>
      <Typography.Title level={4} style={{ margin: '0 0 16px' }}>
        告警管理
      </Typography.Title>
      <Tabs
        activeKey={activeTab}
        onChange={(key) => {
          selectTab(key);
          if (key === 'group') setFilterGroupId(undefined);
        }}
        items={items}
      />
    </div>
  );
};

export default AlarmManage;
