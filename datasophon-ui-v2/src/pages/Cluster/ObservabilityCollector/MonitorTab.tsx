import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Badge } from 'antd';
import { useRef } from 'react';

import { type CollectorNodeMetrics, getCollectorMonitor } from './service';

interface MonitorTabProps {
  clusterId: number;
}

const MonitorTab: React.FC<MonitorTabProps> = ({ clusterId }) => {
  const intl = useIntl();
  const actionRef = useRef<ActionType>(null);
  const columns: ProColumns<CollectorNodeMetrics>[] = [
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.hostname',
        defaultMessage: 'Collector node',
      }),
      dataIndex: 'hostname',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.health',
        defaultMessage: 'Health',
      }),
      dataIndex: 'healthy',
      render: (_, record) => (
        <Badge
          status={record.healthy ? 'success' : 'error'}
          text={
            record.healthy
              ? intl.formatMessage({
                  id: 'pages.observabilityCollector.healthy',
                  defaultMessage: 'Healthy',
                })
              : record.error ||
                intl.formatMessage({
                  id: 'pages.observabilityCollector.unhealthy',
                  defaultMessage: 'Unhealthy',
                })
          }
        />
      ),
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.queue',
        defaultMessage: 'Queue',
      }),
      search: false,
      render: (_, record) =>
        record.metrics
          ? `${record.metrics.queueSize} / ${record.metrics.queueCapacity}`
          : '-',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.sent',
        defaultMessage: 'Sent',
      }),
      search: false,
      render: (_, record) => record.metrics?.sentTotal ?? '-',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.failed',
        defaultMessage: 'Failed / dropped',
      }),
      search: false,
      render: (_, record) =>
        record.metrics
          ? record.metrics.sendFailedTotal +
            record.metrics.refusedTotal +
            record.metrics.processorDroppedTotal
          : '-',
    },
  ];

  return (
    <ProTable<CollectorNodeMetrics>
      actionRef={actionRef}
      rowKey="hostname"
      columns={columns}
      search={false}
      pagination={false}
      request={async () => {
        const result = await getCollectorMonitor(clusterId);
        const data = result.data ?? [];
        return { data, success: result.code === 200, total: data.length };
      }}
    />
  );
};

export default MonitorTab;
