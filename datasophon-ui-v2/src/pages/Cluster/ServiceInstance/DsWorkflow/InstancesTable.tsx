import { type ProColumns, ProTable } from '@ant-design/pro-components';
import { Alert, Button } from 'antd';
import { useCallback, useState } from 'react';
import { useIntl } from '@umijs/max';
import { classifyDsError, type DsErrorKind } from './errors';
import { formatDsTime, formatDuration } from './formatters';
import { getDsWorkflowInstances } from './service';
import styles from './DsWorkflow.module.less';

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
  const [error, setError] = useState<DsErrorKind>();
  const [reloadKey, setReloadKey] = useState(0);
  const loadInstances = useCallback(async () => {
    try {
      const response = await getDsWorkflowInstances(
        clusterId,
        projectCode,
        workflowCode,
      );
      setError(undefined);
      return {
        data: response.data?.list ?? [],
        success: response.success !== false,
        total: response.data?.total ?? 0,
      };
    } catch (requestError) {
      setError(classifyDsError(requestError));
      return { data: [], success: false, total: 0 };
    }
  }, [clusterId, projectCode, workflowCode]);
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
    <>
      {error ? (
        <Alert
          className={styles.instancesError}
          showIcon
          type="error"
          title={intl.formatMessage({ id: `dsWorkflow.error.${error}` })}
          action={
            <Button
              size="small"
              onClick={() => setReloadKey((current) => current + 1)}
            >
              {intl.formatMessage({ id: 'dsWorkflow.retry' })}
            </Button>
          }
        />
      ) : null}
      <ProTable<DATASOPHON.DsWorkflowInstance>
        key={reloadKey}
        rowKey="id"
        className={styles.instancesTable}
        columns={columns}
        search={false}
        options={false}
        pagination={false}
        request={loadInstances}
        onRow={(record) => ({
          onClick: () => onOpen(record),
        })}
      />
    </>
  );
};

export default InstancesTable;
