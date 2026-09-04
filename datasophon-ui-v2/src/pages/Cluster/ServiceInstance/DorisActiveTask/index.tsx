import { Button, Space, Tag, Typography } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getApiFailureMessage } from '@/utils/apiResponse';
import ActiveTaskTable from './ActiveTaskTable';
import styles from './DorisActiveTask.module.less';
import FilterBar from './FilterBar';
import { useActiveTaskPolling } from './hooks/useActiveTaskPolling';
import StatusBanners from './StatusBanners';
import { getDorisActiveTaskDetail, getDorisActiveTasks } from './service';
import TaskDetailDrawer from './TaskDetailDrawer';
import type {
  DorisActiveTaskQuery,
  DorisActiveTask as DorisActiveTaskRow,
} from './types';

interface DorisActiveTaskProps {
  clusterId: number;
  instanceId: number;
}

function useTabPanelActive() {
  const anchorRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(true);

  useEffect(() => {
    const panel = anchorRef.current?.closest<HTMLElement>('[role="tabpanel"]');
    if (!panel) return;

    const update = () => {
      setActive(
        panel.getAttribute('aria-hidden') !== 'true' &&
          !panel.classList.contains('ant-tabs-tabpane-hidden'),
      );
    };
    update();
    const observer = new MutationObserver(update);
    observer.observe(panel, {
      attributes: true,
      attributeFilter: ['aria-hidden', 'class'],
    });
    return () => observer.disconnect();
  }, []);

  return { anchorRef, active };
}

const DorisActiveTask: React.FC<DorisActiveTaskProps> = ({
  clusterId,
  instanceId,
}) => {
  const { anchorRef, active: tabActive } = useTabPanelActive();
  const [filters, setFilters] = useState<DorisActiveTaskQuery>({});
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [selectedTask, setSelectedTask] = useState<DorisActiveTaskRow>();
  const fetcher = useCallback(async () => {
    const response = await getDorisActiveTasks(clusterId, instanceId, filters);
    const failure = getApiFailureMessage(response);
    if (failure) throw new Error(failure);
    if (!response.data) throw new Error('活动任务响应无数据');
    return response.data;
  }, [clusterId, filters, instanceId]);
  const polling = useActiveTaskPolling({
    fetcher,
    active: tabActive,
    autoRefresh,
  });
  const response = polling.error ? undefined : polling.data;

  useEffect(() => {
    if (tabActive) polling.refresh();
  }, [filters, polling.refresh, tabActive]);

  useEffect(() => {
    if (polling.error) {
      setAutoRefresh(false);
      setSelectedTask(undefined);
    }
  }, [polling.error]);

  const handleFilterChange = (nextFilters: DorisActiveTaskQuery) => {
    setFilters(nextFilters);
  };

  const handleOpen = (task: DorisActiveTaskRow) => {
    setSelectedTask(task);
    if (task.type.toUpperCase() === 'LOAD') return;
    void getDorisActiveTaskDetail(clusterId, instanceId, task.taskId)
      .then((detailResponse) => {
        if (getApiFailureMessage(detailResponse) || !detailResponse.data) return;
        setSelectedTask((current) =>
          current?.taskId === task.taskId ? detailResponse.data : current,
        );
      })
      .catch(() => undefined);
  };

  return (
    <div
      ref={anchorRef}
      className={styles.panel}
      data-cluster-id={clusterId}
      data-instance-id={instanceId}
      data-tab-active={tabActive}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space wrap>
          <Typography.Text strong>连接地址：</Typography.Text>
          <Typography.Text data-testid="doris-active-task-connected-host">
            {response?.connectedHostPort || '–'}
          </Typography.Text>
          {response?.serverVersion ? (
            <Typography.Text
              type="secondary"
              data-testid="doris-active-task-server-version"
            >
              {response.serverVersion}
            </Typography.Text>
          ) : null}
          <Tag color="gold" data-experimental="queue-features">
            排队相关字段与分组排序：实验性
          </Tag>
          {polling.lastUpdatedAt ? (
            <Typography.Text data-testid="doris-active-task-last-updated">
              最后更新：{new Date(polling.lastUpdatedAt).toLocaleTimeString()}
            </Typography.Text>
          ) : null}
        </Space>
        <FilterBar
          value={filters}
          loading={polling.loading}
          autoRefresh={autoRefresh}
          onChange={handleFilterChange}
          onRefresh={polling.refresh}
          onAutoRefreshChange={setAutoRefresh}
        />
        <StatusBanners
          response={response}
          error={polling.error}
          loading={polling.loading}
        />
        {response ? (
          <ActiveTaskTable
            tasks={response.tasks}
            loading={polling.loading}
            onOpen={handleOpen}
          />
        ) : null}
        <TaskDetailDrawer
          task={selectedTask}
          open={selectedTask != null}
          onClose={() => setSelectedTask(undefined)}
        />
        {polling.error ? <Button onClick={polling.refresh}>重试</Button> : null}
      </Space>
    </div>
  );
};

export default DorisActiveTask;
