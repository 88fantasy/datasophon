import { PageContainer } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Tabs } from 'antd';
import { useContext, useState } from 'react';

import ClusterContext from '@/context/ClusterContext';

import ConfigTab from './ConfigTab';
import LogsTab from './LogsTab';
import MonitorTab from './MonitorTab';
import TracesTab from './TracesTab';

const ObservabilityCollector: React.FC = () => {
  const intl = useIntl();
  const cluster = useContext(ClusterContext);
  const clusterId = cluster?.clusterId ?? 0;
  const [activeTab, setActiveTab] = useState('config');
  const [linkedTraceId, setLinkedTraceId] = useState<string>();

  return (
    <PageContainer
      title={intl.formatMessage({
        id: 'pages.observabilityCollector.title',
        defaultMessage: 'Collector console',
      })}
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'config',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.config',
              defaultMessage: 'Configuration',
            }),
            children: <ConfigTab clusterId={clusterId} />,
          },
          {
            key: 'monitor',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.monitor',
              defaultMessage: 'Monitoring',
            }),
            children: <MonitorTab clusterId={clusterId} />,
          },
          {
            key: 'traces',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.traces',
              defaultMessage: 'Traces',
            }),
            children: (
              <TracesTab
                clusterId={clusterId}
                onShowLogs={(traceId) => {
                  setLinkedTraceId(traceId);
                  setActiveTab('logs');
                }}
              />
            ),
          },
          {
            key: 'logs',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.logs',
              defaultMessage: 'Logs',
            }),
            children: (
              <LogsTab
                clusterId={clusterId}
                traceId={linkedTraceId}
                onTraceIdConsumed={() => setLinkedTraceId(undefined)}
              />
            ),
          },
        ]}
      />
    </PageContainer>
  );
};

export default ObservabilityCollector;
