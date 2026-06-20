import { PageContainer } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Tabs } from 'antd';
import { useContext } from 'react';

import ClusterContext from '@/context/ClusterContext';

import ConfigTab from './ConfigTab';
import MonitorTab from './MonitorTab';

const ObservabilityCollector: React.FC = () => {
  const intl = useIntl();
  const cluster = useContext(ClusterContext);
  const clusterId = cluster?.clusterId ?? 0;

  return (
    <PageContainer
      title={intl.formatMessage({
        id: 'pages.observabilityCollector.title',
        defaultMessage: 'Collector console',
      })}
    >
      <Tabs
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
        ]}
      />
    </PageContainer>
  );
};

export default ObservabilityCollector;
