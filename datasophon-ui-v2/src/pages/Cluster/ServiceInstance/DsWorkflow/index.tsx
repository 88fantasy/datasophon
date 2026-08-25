import { type ProColumns, ProTable } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Alert, Button, Select, Space, Tag, Typography } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { getDsProjects, getDsWorkflows } from '@/services/dsWorkflow';
import DsDagDrawer from './DsDagDrawer';
import { classifyDsError, type DsErrorKind } from './errors';
import { formatDsTime } from './formatters';
import InstancesTable from './InstancesTable';

interface DsWorkflowPanelProps {
  clusterId: number;
  instanceId: number;
}

const DsWorkflowPanel: React.FC<DsWorkflowPanelProps> = ({
  clusterId,
  instanceId,
}) => {
  const intl = useIntl();
  const [projects, setProjects] = useState<DATASOPHON.DsProject[]>([]);
  const [projectCode, setProjectCode] = useState<number>();
  const [selectedInstance, setSelectedInstance] =
    useState<DATASOPHON.DsWorkflowInstance>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<DsErrorKind>();

  const loadProjects = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const response = await getDsProjects(clusterId);
      const list = response.data?.list ?? [];
      setProjects(list);
      setProjectCode((current) =>
        current != null && list.some((project) => project.code === current)
          ? current
          : list[0]?.code,
      );
    } catch (requestError) {
      setError(classifyDsError(requestError));
    } finally {
      setLoading(false);
    }
  }, [clusterId]);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  const columns = useMemo<ProColumns<DATASOPHON.DsWorkflowDefinition>[]>(
    () => [
      {
        title: intl.formatMessage({ id: 'dsWorkflow.table.name' }),
        dataIndex: 'name',
      },
      {
        title: intl.formatMessage({ id: 'dsWorkflow.table.releaseState' }),
        dataIndex: 'releaseState',
        width: 130,
        search: false,
        render: (_, record) => (
          <Tag color={record.releaseState === 'ONLINE' ? 'success' : 'default'}>
            {intl.formatMessage({
              id:
                record.releaseState === 'ONLINE'
                  ? 'dsWorkflow.status.online'
                  : 'dsWorkflow.status.offline',
            })}
          </Tag>
        ),
      },
      {
        title: intl.formatMessage({ id: 'dsWorkflow.table.version' }),
        dataIndex: 'version',
        width: 90,
        search: false,
      },
      {
        title: intl.formatMessage({ id: 'dsWorkflow.table.owner' }),
        dataIndex: 'owner',
        width: 130,
        search: false,
        render: (_, record) => record.owner || '—',
      },
      {
        title: intl.formatMessage({ id: 'dsWorkflow.table.updateTime' }),
        dataIndex: 'updateTime',
        width: 190,
        search: false,
        render: (_, record) => formatDsTime(record.updateTime),
      },
    ],
    [intl],
  );

  return (
    <div data-instance-id={instanceId} style={{ padding: '8px 0' }}>
      <div style={{ display: 'grid', gap: 24 }}>
        <Space>
          <Typography.Text strong>
            {intl.formatMessage({ id: 'dsWorkflow.project.label' })}
          </Typography.Text>
          <Select<number>
            aria-label={intl.formatMessage({ id: 'dsWorkflow.project.label' })}
            loading={loading}
            value={projectCode}
            placeholder={intl.formatMessage({
              id: 'dsWorkflow.project.placeholder',
            })}
            options={projects.map((project) => ({
              label: project.name,
              value: project.code,
            }))}
            showSearch={{ optionFilterProp: 'label' }}
            style={{ minWidth: 280 }}
            onChange={(value) => {
              setProjectCode(value);
              setSelectedInstance(undefined);
            }}
          />
        </Space>

        {error ? (
          <Alert
            type="error"
            showIcon
            title={intl.formatMessage({ id: `dsWorkflow.error.${error}` })}
            action={
              <Button size="small" onClick={() => void loadProjects()}>
                {intl.formatMessage({ id: 'dsWorkflow.retry' })}
              </Button>
            }
          />
        ) : null}

        {projectCode == null && !error ? (
          <Alert
            type="info"
            showIcon
            title={intl.formatMessage({ id: 'dsWorkflow.project.empty' })}
          />
        ) : null}

        {projectCode != null ? (
          <ProTable<DATASOPHON.DsWorkflowDefinition>
            key={projectCode}
            rowKey="code"
            columns={columns}
            params={{ projectCode }}
            options={false}
            pagination={{ defaultPageSize: 20, showSizeChanger: true }}
            request={async (params) => {
              try {
                const response = await getDsWorkflows(
                  clusterId,
                  projectCode,
                  params.current ?? 1,
                  params.pageSize ?? 20,
                  typeof params.name === 'string' ? params.name : undefined,
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
            }}
            expandable={{
              expandedRowRender: (record) => (
                <InstancesTable
                  clusterId={clusterId}
                  projectCode={projectCode}
                  workflowCode={record.code}
                  onOpen={setSelectedInstance}
                />
              ),
            }}
          />
        ) : null}
      </div>

      {projectCode != null ? (
        <DsDagDrawer
          clusterId={clusterId}
          projectCode={projectCode}
          instance={selectedInstance}
          open={selectedInstance != null}
          onClose={() => setSelectedInstance(undefined)}
        />
      ) : null}
    </div>
  );
};

export default DsWorkflowPanel;
