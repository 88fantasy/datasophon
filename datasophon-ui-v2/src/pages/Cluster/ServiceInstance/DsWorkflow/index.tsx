import { useIntl } from '@umijs/max';
import { Alert, Button, Select, Space, Typography } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { getDsProjects } from '@/services/dsWorkflow';

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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const loadProjects = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await getDsProjects(clusterId);
      const list = response.data?.list ?? [];
      setProjects(list);
      setProjectCode((current) =>
        current != null && list.some((project) => project.code === current)
          ? current
          : list[0]?.code,
      );
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [clusterId]);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

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
        </Space>

        {error ? (
          <Alert
            type="error"
            showIcon
            title={intl.formatMessage({ id: 'dsWorkflow.error.unavailable' })}
            action={
              <Button size="small" onClick={() => void loadProjects()}>
                {intl.formatMessage({ id: 'dsWorkflow.retry' })}
              </Button>
            }
          />
        ) : (
          <Alert
            type="info"
            showIcon
            title={
              projectCode == null
                ? intl.formatMessage({ id: 'dsWorkflow.project.empty' })
                : intl.formatMessage({ id: 'dsWorkflow.skeleton.ready' })
            }
          />
        )}
      </div>
    </div>
  );
};

export default DsWorkflowPanel;
