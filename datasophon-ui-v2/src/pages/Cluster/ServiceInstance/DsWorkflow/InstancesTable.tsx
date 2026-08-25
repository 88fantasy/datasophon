import { type ProColumns, ProTable } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { formatDsTime, formatDuration } from './formatters';
import { getDsWorkflowInstances } from './service';

interface InstancesTableProps {
  clusterId: number;
  projectCode: number;
  workflowCode: number;
  onOpen: (instance: DATASOPHON.DsWorkflowInstance) => void;
}

const InstancesTable: React.FC<InstancesTableProps> = ({
  clusterId,
  projectCode,
  workflowCode,
  onOpen,
}) => {
  const intl = useIntl();
  const columns: ProColumns<DATASOPHON.DsWorkflowInstance>[] = [
    {
      title: intl.formatMessage({ id: 'dsWorkflow.table.instanceName' }),
      dataIndex: 'name',
    },
    {
      title: intl.formatMessage({ id: 'dsWorkflow.table.state' }),
      dataIndex: 'state',
      width: 170,
    },
    {
      title: intl.formatMessage({ id: 'dsWorkflow.table.startTime' }),
      dataIndex: 'startTime',
      width: 190,
      render: (_, record) => formatDsTime(record.startTime),
    },
    {
      title: intl.formatMessage({ id: 'dsWorkflow.table.duration' }),
      dataIndex: 'durationSeconds',
      width: 110,
      render: (_, record) => formatDuration(record.durationSeconds),
    },
    {
      title: intl.formatMessage({ id: 'dsWorkflow.table.host' }),
      dataIndex: 'host',
      width: 180,
      render: (_, record) => record.host || '—',
    },
  ];

  return (
    <ProTable<DATASOPHON.DsWorkflowInstance>
      rowKey="id"
      columns={columns}
      search={false}
      options={false}
      pagination={false}
      request={async () => {
        const response = await getDsWorkflowInstances(
          clusterId,
          projectCode,
          workflowCode,
        );
        return {
          data: response.data?.list ?? [],
          success: response.success !== false,
          total: response.data?.total ?? 0,
        };
      }}
      onRow={(record) => ({
        onClick: () => onOpen(record),
        style: { cursor: 'pointer' },
      })}
    />
  );
};

export default InstancesTable;
