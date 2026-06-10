import { PageContainer } from '@ant-design/pro-components';
import { Tabs } from 'antd';
import React, { useContext, useState } from 'react';
import { ClusterContext } from '@/context/ClusterContext';
import GroupTab from './Group';
import MetricTab from './Metric';

const AlarmManage: React.FC = () => {
  const { clusterId } = useContext(ClusterContext);
  const [activeTab, setActiveTab] = useState('group');
  const [filterGroupId, setFilterGroupId] = useState<number | undefined>();

  const handleViewMetrics = (groupId: number) => {
    setFilterGroupId(groupId);
    setActiveTab('metric');
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
  ];

  return (
    <PageContainer title="告警管理" style={{ minHeight: '100%' }}>
      <Tabs
        activeKey={activeTab}
        onChange={(key) => {
          setActiveTab(key);
          if (key === 'group') setFilterGroupId(undefined);
        }}
        items={items}
      />
    </PageContainer>
  );
};

export default AlarmManage;
