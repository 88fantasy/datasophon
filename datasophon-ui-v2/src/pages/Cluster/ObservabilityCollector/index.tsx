import { ReloadOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Button, DatePicker, Select, Space, Tabs, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useContext, useEffect, useState } from 'react';

import ClusterContext from '@/context/ClusterContext';

import LogsTab from './LogsTab';
import { useObservabilityStyles } from './observabilityStyles';
import type { ObservabilityTimeRange } from './observabilityTypes';
import TopologyTab from './TopologyTab';
import TracesTab from './TracesTab';

const { RangePicker } = DatePicker;

const ObservabilityCollector: React.FC = () => {
  const intl = useIntl();
  const { styles } = useObservabilityStyles();
  const cluster = useContext(ClusterContext);
  const clusterId = cluster?.clusterId ?? 0;
  const [activeTab, setActiveTab] = useState('topology');
  const [linkedTraceId, setLinkedTraceId] = useState<string>();
  const [linkedServiceName, setLinkedServiceName] = useState<string>();
  const [timeRange, setTimeRange] = useState<ObservabilityTimeRange>([
    dayjs().subtract(1, 'hour'),
    dayjs(),
  ]);
  const [refreshInterval, setRefreshInterval] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  const t = (id: string, defaultMessage: string) =>
    intl.formatMessage({ id, defaultMessage });

  useEffect(() => {
    if (!refreshInterval) return;
    const timer = window.setInterval(() => {
      setTimeRange(([start, end]) => {
        const duration = end.diff(start);
        const nextEnd = dayjs();
        return [nextEnd.subtract(duration, 'millisecond'), nextEnd];
      });
      setRefreshKey((value) => value + 1);
    }, refreshInterval * 1000);
    return () => window.clearInterval(timer);
  }, [refreshInterval]);

  const updateTimeRange = (nextRange: ObservabilityTimeRange) => {
    setTimeRange(nextRange);
    setRefreshKey((value) => value + 1);
  };

  return (
    <div className={styles.workspace}>
      <div className={styles.workspaceHeader}>
        <div>
          <Space size={10}>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {t(
                'pages.observabilityCollector.workspaceTitle',
                '链路跟踪工作台',
              )}
            </Typography.Title>
            <Tag color="processing">OpenTelemetry</Tag>
          </Space>
          <Typography.Text
            type="secondary"
            className={styles.workspaceSubtitle}
          >
            {t(
              'pages.observabilityCollector.workspaceSubtitle',
              '从服务依赖到单次请求和关联日志，统一定位性能瓶颈与异常。',
            )}
          </Typography.Text>
        </div>
        <Space wrap className={styles.workspaceControls}>
          <RangePicker
            showTime
            allowClear={false}
            value={timeRange}
            onChange={(value) => {
              if (value?.[0] && value[1]) {
                updateTimeRange([value[0], value[1]]);
              }
            }}
          />
          <Select
            value={refreshInterval}
            style={{ width: 132 }}
            onChange={setRefreshInterval}
            options={[
              {
                value: 0,
                label: t(
                  'pages.observabilityCollector.autoRefreshOff',
                  '自动刷新：关',
                ),
              },
              {
                value: 30,
                label: t(
                  'pages.observabilityCollector.autoRefresh30s',
                  '每 30 秒刷新',
                ),
              },
              {
                value: 60,
                label: t(
                  'pages.observabilityCollector.autoRefresh60s',
                  '每 60 秒刷新',
                ),
              },
            ]}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => setRefreshKey((value) => value + 1)}
          >
            {t('pages.observabilityCollector.refresh', '刷新')}
          </Button>
        </Space>
      </div>
      <Tabs
        className={styles.workspaceTabs}
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'topology',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.topology',
              defaultMessage: '服务拓扑',
            }),
            children: (
              <TopologyTab
                clusterId={clusterId}
                timeRange={timeRange}
                refreshKey={refreshKey}
                onShowTraces={(serviceName) => {
                  setLinkedServiceName(serviceName);
                  setActiveTab('traces');
                }}
              />
            ),
          },
          {
            key: 'traces',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.traces',
              defaultMessage: 'Trace 检索',
            }),
            children: (
              <TracesTab
                clusterId={clusterId}
                timeRange={timeRange}
                refreshKey={refreshKey}
                onShowLogs={(traceId) => {
                  setLinkedTraceId(traceId);
                  setActiveTab('logs');
                }}
                serviceName={linkedServiceName}
                onServiceNameConsumed={() => setLinkedServiceName(undefined)}
              />
            ),
          },
          {
            key: 'logs',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.logs',
              defaultMessage: '日志检索',
            }),
            children: (
              <LogsTab
                clusterId={clusterId}
                timeRange={timeRange}
                refreshKey={refreshKey}
                traceId={linkedTraceId}
                onTraceIdConsumed={() => setLinkedTraceId(undefined)}
              />
            ),
          },
        ]}
      />
    </div>
  );
};

export default ObservabilityCollector;
