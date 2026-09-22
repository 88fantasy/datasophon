import type { TableColumnsType } from 'antd';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Descriptions,
  Drawer,
  Segmented,
  Space,
  Statistic,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getApiFailureMessage } from '@/utils/apiResponse';
import {
  getSeaTunnelJobInfo,
  getSeaTunnelJobs,
  getSeaTunnelOverview,
  getSeaTunnelPendingJobs,
  getSeaTunnelWorkers,
} from './service';
import type {
  SeaTunnelJobInfo,
  SeaTunnelJobState,
  SeaTunnelOverview,
  SeaTunnelPendingJobs,
  SeaTunnelWorker,
} from './types';

interface SeaTunnelJobsProps {
  clusterId: number;
  instanceId: number;
}

const POLL_INTERVAL_MS = 10_000;

function useTabPanelActive() {
  const anchorRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(false);

  useEffect(() => {
    const panel = anchorRef.current?.closest<HTMLElement>('[role="tabpanel"]');
    if (!panel) {
      setActive(true);
      return;
    }

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

function display(value?: string | null) {
  return value == null || value === '' ? '–' : value;
}

function responseData<T>(
  response: DATASOPHON.ApiResponse<T>,
  fallback: string,
) {
  const failure = getApiFailureMessage(response, fallback);
  if (failure) throw new Error(failure);
  if (response.data == null) throw new Error(fallback);
  return response.data;
}

function errorMessage(error: unknown) {
  if (error != null && typeof error === 'object') {
    const value = error as {
      data?: unknown;
      info?: unknown;
      message?: unknown;
      response?: { data?: unknown };
    };
    const responseFailure = getApiFailureMessage(value.response?.data, '');
    if (responseFailure) return responseFailure;
    const dataFailure = getApiFailureMessage(value.data, '');
    if (dataFailure) return dataFailure;
    const infoFailure = getApiFailureMessage(value.info, '');
    if (infoFailure) return infoFailure;
    if (typeof value.message === 'string' && value.message)
      return value.message;
  }
  return 'SeaTunnel 作业数据加载失败';
}

const overviewFields: Array<{
  key: keyof SeaTunnelOverview;
  label: string;
  testId?: string;
}> = [
  { key: 'projectVersion', label: '集群版本' },
  { key: 'gitCommitAbbrev', label: 'Git Commit' },
  { key: 'totalSlot', label: '总 slot', testId: 'seatunnel-total-slot' },
  { key: 'runningJobs', label: '运行中作业' },
  { key: 'finishedJobs', label: '已完成作业' },
  { key: 'failedJobs', label: '失败作业' },
  { key: 'pendingJobs', label: '排队作业' },
  { key: 'cancelledJobs', label: '已取消作业' },
  { key: 'workers', label: 'Worker' },
];

const workerColumns: TableColumnsType<SeaTunnelWorker> = [
  { title: 'Worker 地址', dataIndex: 'address', render: display },
  { title: '已用 slot', dataIndex: 'usedSlots', render: display },
  { title: '总 slot', dataIndex: 'totalSlots', render: display },
  {
    title: '运行中 Job ID',
    dataIndex: 'runningJobIds',
    render: (ids?: string[] | null) => (ids?.length ? ids.join(', ') : '–'),
  },
  {
    title: '堆内存（字节）',
    dataIndex: 'totalHeapMemoryBytes',
    render: display,
  },
];

const jobColumns: TableColumnsType<SeaTunnelJobInfo> = [
  { title: '作业 ID', dataIndex: 'jobId', render: display },
  { title: '作业名称', dataIndex: 'jobName', render: display },
  { title: '状态', dataIndex: 'jobStatus', render: display },
  { title: '提交时间', dataIndex: 'createTime', render: display },
  { title: '完成时间', dataIndex: 'finishTime', render: display },
];

const SeaTunnelJobs: React.FC<SeaTunnelJobsProps> = ({
  clusterId,
  instanceId,
}) => {
  const { anchorRef, active: tabActive } = useTabPanelActive();
  const [jobState, setJobState] = useState<SeaTunnelJobState>('running');
  const [overview, setOverview] = useState<SeaTunnelOverview>();
  const [workers, setWorkers] = useState<SeaTunnelWorker[]>([]);
  const [pending, setPending] = useState<SeaTunnelPendingJobs>();
  const [jobs, setJobs] = useState<SeaTunnelJobInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [selectedJobId, setSelectedJobId] = useState<string>();
  const [jobInfo, setJobInfo] = useState<SeaTunnelJobInfo>();
  const [jobInfoLoading, setJobInfoLoading] = useState(false);
  const [jobInfoError, setJobInfoError] = useState<string>();
  const refreshRequestId = useRef(0);
  const jobInfoRequestId = useRef(0);

  const refresh = useCallback(async () => {
    const requestId = ++refreshRequestId.current;
    setLoading(true);
    setError(undefined);
    try {
      const [overviewResponse, workersResponse, pendingResponse, jobsResponse] =
        await Promise.all([
          getSeaTunnelOverview(clusterId, instanceId),
          getSeaTunnelWorkers(clusterId, instanceId),
          getSeaTunnelPendingJobs(clusterId, instanceId),
          getSeaTunnelJobs(clusterId, instanceId, jobState),
        ]);
      const nextOverview = responseData(overviewResponse, '集群概览加载失败');
      const nextWorkers = responseData(workersResponse, 'Worker 资源加载失败');
      const nextPending = responseData(pendingResponse, '排队作业加载失败');
      const nextJobs = responseData(jobsResponse, '作业列表加载失败');
      if (requestId !== refreshRequestId.current) return;
      setOverview(nextOverview);
      setWorkers(nextWorkers.workers ?? []);
      setPending(nextPending);
      setJobs(nextJobs);
    } catch (requestError) {
      if (requestId === refreshRequestId.current)
        setError(errorMessage(requestError));
    } finally {
      if (requestId === refreshRequestId.current) setLoading(false);
    }
  }, [clusterId, instanceId, jobState]);

  useEffect(() => {
    if (!tabActive) return;

    let cancelled = false;
    let timer: number | undefined;
    const poll = async () => {
      await refresh();
      if (!cancelled) timer = window.setTimeout(poll, POLL_INTERVAL_MS);
    };
    void poll();
    return () => {
      cancelled = true;
      refreshRequestId.current += 1;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [refresh, tabActive]);

  const openJobInfo = async (jobId?: string | null) => {
    if (!jobId) return;
    const requestId = ++jobInfoRequestId.current;
    setSelectedJobId(jobId);
    setJobInfo(undefined);
    setJobInfoError(undefined);
    setJobInfoLoading(true);
    try {
      const response = await getSeaTunnelJobInfo(clusterId, instanceId, jobId);
      const nextJobInfo = responseData(response, '作业详情加载失败');
      if (requestId === jobInfoRequestId.current) setJobInfo(nextJobInfo);
    } catch (requestError) {
      if (requestId === jobInfoRequestId.current)
        setJobInfoError(errorMessage(requestError));
    } finally {
      if (requestId === jobInfoRequestId.current) setJobInfoLoading(false);
    }
  };

  const closeJobInfo = () => {
    jobInfoRequestId.current += 1;
    setSelectedJobId(undefined);
  };

  const pendingJobs = pending?.pendingJobs ?? [];
  const pendingCount =
    pending?.queueSummary?.size ?? String(pendingJobs.length);
  const hasPendingJobs = Number(pendingCount) > 0 || pendingJobs.length > 0;
  const columns: TableColumnsType<SeaTunnelJobInfo> = [
    ...jobColumns,
    {
      title: '操作',
      key: 'action',
      render: (_, job) => (
        <Button
          type="link"
          disabled={!job.jobId}
          onClick={() => void openJobInfo(job.jobId)}
        >
          详情
        </Button>
      ),
    },
  ];

  return (
    <div ref={anchorRef} data-tab-active={tabActive}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        {error ? <Alert type="error" showIcon title={error} /> : null}
        <Card title="SeaTunnel 集群概览" extra="动态 slot 模式">
          <div
            style={{
              display: 'grid',
              gap: 16,
              gridTemplateColumns: 'repeat(auto-fit, minmax(145px, 1fr))',
            }}
          >
            {overviewFields.map(({ key, label, testId }) => (
              <div key={key} data-testid={testId}>
                <Statistic title={label} value={display(overview?.[key])} />
              </div>
            ))}
          </div>
        </Card>
        <Card title="Worker 资源">
          <div data-testid="seatunnel-workers-table">
            <Table<SeaTunnelWorker>
              columns={workerColumns}
              dataSource={workers}
              loading={loading && overview == null}
              pagination={false}
              rowKey={(worker, index) => worker.address || `worker-${index}`}
              size="small"
            />
          </div>
        </Card>
        <Collapse
          activeKey={hasPendingJobs ? ['pending'] : []}
          items={[
            {
              key: 'pending',
              label: `排队作业（${display(pendingCount)}）`,
              children: (
                <Table<SeaTunnelJobInfo>
                  columns={jobColumns}
                  dataSource={pendingJobs}
                  loading={loading && pending == null}
                  pagination={false}
                  rowKey={(job, index) => job.jobId || `pending-${index}`}
                  size="small"
                />
              ),
            },
          ]}
        />
        <Card
          title="作业列表"
          extra={
            <Segmented
              aria-label="作业状态"
              options={[
                { label: '运行中', value: 'running' },
                { label: '已完成', value: 'finished' },
              ]}
              value={jobState}
              onChange={(value) => setJobState(value as SeaTunnelJobState)}
            />
          }
        >
          <div data-testid="seatunnel-jobs-table">
            <Table<SeaTunnelJobInfo>
              columns={columns}
              dataSource={jobs}
              loading={loading}
              pagination={false}
              rowKey={(job, index) => job.jobId || `job-${index}`}
              size="small"
            />
          </div>
        </Card>
        <Drawer
          open={selectedJobId != null}
          title={`作业详情：${display(selectedJobId)}`}
          onClose={closeJobInfo}
        >
          {jobInfoError ? (
            <Alert type="error" showIcon title={jobInfoError} />
          ) : jobInfoLoading ? (
            <span>加载中…</span>
          ) : jobInfo ? (
            <Descriptions column={1} size="small">
              <Descriptions.Item label="作业 ID">
                {display(jobInfo.jobId)}
              </Descriptions.Item>
              <Descriptions.Item label="作业名称">
                {display(jobInfo.jobName)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {display(jobInfo.jobStatus)}
              </Descriptions.Item>
              <Descriptions.Item label="提交时间">
                {display(jobInfo.createTime)}
              </Descriptions.Item>
              <Descriptions.Item label="完成时间">
                {display(jobInfo.finishTime)}
              </Descriptions.Item>
              {Object.entries(jobInfo.metrics ?? {}).length > 0 ? (
                Object.entries(jobInfo.metrics ?? {}).map(([name, value]) => (
                  <Descriptions.Item key={name} label={name}>
                    {display(value)}
                  </Descriptions.Item>
                ))
              ) : (
                <Descriptions.Item label="指标">–</Descriptions.Item>
              )}
            </Descriptions>
          ) : (
            <span>–</span>
          )}
        </Drawer>
      </Space>
    </div>
  );
};

export default SeaTunnelJobs;
