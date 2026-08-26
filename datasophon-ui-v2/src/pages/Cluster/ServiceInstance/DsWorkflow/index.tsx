import { type ProColumns, ProTable } from '@ant-design/pro-components';
import { history, useIntl } from '@umijs/max';
import { Alert, Button, Select, Space, Tag, Typography } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { classifyDsError, type DsErrorKind } from './errors';
import { formatDsTime } from './formatters';
import InstancesTable from './InstancesTable';
import { getDsProjects, getDsWorkflows } from './service';

interface DsWorkflowPanelProps {
  clusterId: number;
  instanceId: number;
  dsWebUrl?: string;
}

const DsWorkflowPanel: React.FC<DsWorkflowPanelProps> = ({
  clusterId,
  instanceId,
  dsWebUrl,
}) => {
  const intl = useIntl();
  const [projects, setProjects] = useState<DATASOPHON.DsProject[]>([]);
  const [projectCode, setProjectCode] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<DsErrorKind>();
  const [releaseStateFilter, setReleaseStateFilter] = useState('ALL');

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
      {
        title: intl.formatMessage({ id: 'dsWorkflow.table.action' }),
        key: 'action',
        width: 120,
        search: false,
        render: () =>
          dsWebUrl ? (
            <Button
              type="link"
              size="small"
              href={dsWebUrl}
              target="_blank"
              rel="noreferrer"
            >
              {intl.formatMessage({ id: 'dsWorkflow.action.openDs' })}
            </Button>
          ) : (
            '—'
          ),
      },
    ],
    [dsWebUrl, intl],
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
            onChange={setProjectCode}
          />
          <Select<string>
            aria-label={intl.formatMessage({
              id: 'dsWorkflow.filter.releaseState',
            })}
            value={releaseStateFilter}
            options={[
              {
                label: intl.formatMessage({ id: 'dsWorkflow.filter.all' }),
                value: 'ALL',
              },
              {
                label: intl.formatMessage({ id: 'dsWorkflow.status.online' }),
                value: 'ONLINE',
              },
              {
                label: intl.formatMessage({ id: 'dsWorkflow.status.offline' }),
                value: 'OFFLINE',
              },
            ]}
            style={{ minWidth: 150 }}
            onChange={setReleaseStateFilter}
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
            key={`${projectCode}-${releaseStateFilter}`}
            rowKey="code"
            columns={columns}
            params={{ projectCode, releaseStateFilter }}
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
                  data: (response.data?.list ?? []).filter(
                    (workflow) =>
                      releaseStateFilter === 'ALL' ||
                      workflow.releaseState === releaseStateFilter,
                  ),
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
                  onOpen={(instance) =>
                    history.push(
                      `/cluster/${clusterId}/service/${instanceId}/ds-workflow/${projectCode}/${instance.id}`,
                    )
                  }
                />
              ),
            }}
          />
        ) : null}
      </div>
    </div>
  );
};

export default DsWorkflowPanel;
