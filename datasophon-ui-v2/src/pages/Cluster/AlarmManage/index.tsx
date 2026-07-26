import { AlertOutlined, BellOutlined } from '@ant-design/icons';
import { useSearchParams } from '@umijs/max';
import { Alert, Button, Card, Col, Row, Statistic, Tabs, Typography } from 'antd';
import React, { useContext, useEffect, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import { useClusterSummary } from '../Dashboard/hooks/useClusterSummary';
import GroupTab from './Group';
import HistoryTab from './History';
import MetricTab from './Metric';
import useStyles from './style';

const TAB_KEYS = new Set(['group', 'metric', 'history']);

const AlarmManage: React.FC = () => {
  const { styles } = useStyles();
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;
  const { summary, loading } = useClusterSummary({
    clusterId,
    refreshKey: 0,
  });
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
      <div className={styles.titleRow}>
        <Typography.Title level={4} className={styles.title}>
          告警管理
        </Typography.Title>
        <Button onClick={() => selectTab('history')}>查看告警历史</Button>
      </div>
      <Row gutter={[16, 16]} className={styles.summaryRow}>
        <Col xs={24} md={6}>
          <Card size="small" className={styles.summaryCard}>
            <div className={styles.summaryContent}>
              <span
                className={styles.summaryIcon}
                style={{ color: '#f59e0b', background: '#fff7e6' }}
              >
                <AlertOutlined />
              </span>
              <Statistic
                title="活跃告警"
                value={loading ? '-' : (summary?.stats?.alertTotal ?? 0)}
                styles={{ content: { color: '#d97706', fontWeight: 600 } }}
              />
            </div>
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small" className={styles.summaryCard}>
            <div className={styles.summaryContent}>
              <span
                className={styles.summaryIcon}
                style={{ color: '#ef4444', background: '#fff1f0' }}
              >
                <BellOutlined />
              </span>
              <Statistic
                title="严重告警"
                value={
                  loading ? '-' : (summary?.stats?.criticalAlertTotal ?? 0)
                }
                styles={{ content: { color: '#dc2626', fontWeight: 600 } }}
              />
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Alert
            className={styles.guidance}
            type="info"
            showIcon
            title="处理建议"
            description="优先检查仍处于“告警中”的异常级别记录，再结合告警指标和建议操作定位影响范围。"
          />
        </Col>
      </Row>
      <div className={styles.tabsShell}>
        <Tabs
          activeKey={activeTab}
          onChange={(key) => {
            selectTab(key);
            if (key === 'group') setFilterGroupId(undefined);
          }}
          items={items}
        />
      </div>
    </div>
  );
};

export default AlarmManage;
