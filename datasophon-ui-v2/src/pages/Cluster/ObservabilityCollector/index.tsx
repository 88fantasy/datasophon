import { PageContainer } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Tabs } from 'antd';
import { useContext, useState } from 'react';

import ClusterContext from '@/context/ClusterContext';

import LogsTab from './LogsTab';
import TopologyTab from './TopologyTab';
import TracesTab from './TracesTab';

const ObservabilityCollector: React.FC = () => {
  const intl = useIntl();
  const cluster = useContext(ClusterContext);
  const clusterId = cluster?.clusterId ?? 0;
  const [activeTab, setActiveTab] = useState('topology');
  const [linkedTraceId, setLinkedTraceId] = useState<string>();
  const [linkedServiceName, setLinkedServiceName] = useState<string>();

  return (
    <PageContainer title={false}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'topology',
            label: intl.formatMessage({
              id: 'pages.observabilityCollector.topology',
              defaultMessage: 'Topology',
            }),
            children: (
              <TopologyTab
                clusterId={clusterId}
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
              defaultMessage: 'Traces',
            }),
            children: (
              <TracesTab
                clusterId={clusterId}
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
