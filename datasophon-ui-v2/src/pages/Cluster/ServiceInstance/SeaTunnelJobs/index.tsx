import { useIntl } from '@umijs/max';
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
import { useTabPanelActive } from '../useTabPanelActive';
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

function display(value?: string | null) {
  return value == null || value === '' ? '–' : value;
}

function metricText(value: unknown) {
  return value != null && typeof value === 'object'
    ? JSON.stringify(value)
    : display(value == null ? null : String(value));
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

function errorMessage(error: unknown, fallback: string) {
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
  return fallback;
}

const overviewFields: Array<{
  key: keyof SeaTunnelOverview;
  label: string;
  testId?: string;
}> = [
  { key: 'projectVersion', label: 'overview.projectVersion' },
  { key: 'gitCommitAbbrev', label: 'overview.gitCommitAbbrev' },
  {
    key: 'totalSlot',
    label: 'overview.totalSlot',
    testId: 'seatunnel-total-slot',
  },
  { key: 'runningJobs', label: 'overview.runningJobs' },
  { key: 'finishedJobs', label: 'overview.finishedJobs' },
  { key: 'failedJobs', label: 'overview.failedJobs' },
  { key: 'pendingJobs', label: 'overview.pendingJobs' },
  { key: 'cancelledJobs', label: 'overview.cancelledJobs' },
  { key: 'workers', label: 'overview.workers' },
];

const SeaTunnelJobs: React.FC<SeaTunnelJobsProps> = ({
  clusterId,
  instanceId,
}) => {
  const intl = useIntl();
  const t = (key: string) =>
    intl.formatMessage({ id: `pages.seatunnelJobs.${key}` });
  const workerColumns: TableColumnsType<SeaTunnelWorker> = [
    { title: t('worker.address'), dataIndex: 'address', render: display },
    { title: t('worker.usedSlots'), dataIndex: 'usedSlots', render: display },
    { title: t('worker.totalSlots'), dataIndex: 'totalSlots', render: display },
    {
      title: t('worker.runningJobIds'),
      dataIndex: 'runningJobIds',
      render: (ids?: string[] | null) => (ids?.length ? ids.join(', ') : '–'),
    },
    {
      title: t('worker.heapMemoryBytes'),
      dataIndex: 'totalHeapMemoryBytes',
      render: display,
    },
  ];
  const jobColumns: TableColumnsType<SeaTunnelJobInfo> = [
    { title: t('job.id'), dataIndex: 'jobId', render: display },
    { title: t('job.name'), dataIndex: 'jobName', render: display },
    { title: t('job.status'), dataIndex: 'jobStatus', render: display },
    { title: t('job.createTime'), dataIndex: 'createTime', render: display },
    { title: t('job.finishTime'), dataIndex: 'finishTime', render: display },
  ];
  const { anchorRef, active: tabActive } = useTabPanelActive(false);
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
      const nextOverview = responseData(
        overviewResponse,
        intl.formatMessage({ id: 'pages.seatunnelJobs.error.overview' }),
      );
      const nextWorkers = responseData(
        workersResponse,
        intl.formatMessage({ id: 'pages.seatunnelJobs.error.workers' }),
      );
      const nextPending = responseData(
        pendingResponse,
        intl.formatMessage({ id: 'pages.seatunnelJobs.error.pending' }),
      );
      const nextJobs = responseData(
        jobsResponse,
        intl.formatMessage({ id: 'pages.seatunnelJobs.error.jobs' }),
      );
      if (requestId !== refreshRequestId.current) return;
      setOverview(nextOverview);
      setWorkers(nextWorkers.workers ?? []);
      setPending(nextPending);
      setJobs(nextJobs);
    } catch (requestError) {
      if (requestId === refreshRequestId.current)
        setError(
          errorMessage(
            requestError,
            intl.formatMessage({ id: 'pages.seatunnelJobs.error.load' }),
          ),
        );
    } finally {
      if (requestId === refreshRequestId.current) setLoading(false);
    }
  }, [clusterId, instanceId, jobState, intl]);

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
      const nextJobInfo = responseData(response, t('error.detail'));
      if (requestId === jobInfoRequestId.current) setJobInfo(nextJobInfo);
    } catch (requestError) {
      if (requestId === jobInfoRequestId.current)
        setJobInfoError(errorMessage(requestError, t('error.load')));
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
      title: t('action'),
      key: 'action',
      render: (_, job) => (
        <Button
          type="link"
          disabled={!job.jobId}
          onClick={() => void openJobInfo(job.jobId)}
        >
          {t('detail')}
        </Button>
      ),
    },
  ];

  return (
    <div ref={anchorRef} data-tab-active={tabActive}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        {error ? <Alert type="error" showIcon title={error} /> : null}
        <Card title={t('overview.title')} extra={t('overview.dynamicSlot')}>
          <div
            style={{
              display: 'grid',
              gap: 16,
              gridTemplateColumns: 'repeat(auto-fit, minmax(145px, 1fr))',
            }}
          >
            {overviewFields.map(({ key, label, testId }) => (
              <div key={key} data-testid={testId}>
                <Statistic title={t(label)} value={display(overview?.[key])} />
              </div>
            ))}
          </div>
        </Card>
        <Card title={t('workers.title')}>
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
          // 有无排队作业切换时重挂载以重新决定默认展开，其余时间由用户自由折叠
          key={String(hasPendingJobs)}
          defaultActiveKey={hasPendingJobs ? ['pending'] : []}
          items={[
            {
              key: 'pending',
              label: intl.formatMessage(
                { id: 'pages.seatunnelJobs.pending.title' },
                { count: display(pendingCount) },
              ),
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
          title={t('jobs.title')}
          extra={
            <Segmented
              aria-label={t('jobs.state')}
              options={[
                { label: t('jobs.running'), value: 'running' },
                { label: t('jobs.finished'), value: 'finished' },
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
          title={intl.formatMessage(
            { id: 'pages.seatunnelJobs.detailTitle' },
            { jobId: display(selectedJobId) },
          )}
          onClose={closeJobInfo}
        >
          {jobInfoError ? (
            <Alert type="error" showIcon title={jobInfoError} />
          ) : jobInfoLoading ? (
            <span>{t('loading')}</span>
          ) : jobInfo ? (
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t('job.id')}>
                {display(jobInfo.jobId)}
              </Descriptions.Item>
              <Descriptions.Item label={t('job.name')}>
                {display(jobInfo.jobName)}
              </Descriptions.Item>
              <Descriptions.Item label={t('job.status')}>
                {display(jobInfo.jobStatus)}
              </Descriptions.Item>
              <Descriptions.Item label={t('job.createTime')}>
                {display(jobInfo.createTime)}
              </Descriptions.Item>
              <Descriptions.Item label={t('job.finishTime')}>
                {display(jobInfo.finishTime)}
              </Descriptions.Item>
              {Object.entries(jobInfo.metrics ?? {}).length > 0 ? (
                Object.entries(jobInfo.metrics ?? {}).map(([name, value]) => (
                  <Descriptions.Item key={name} label={name}>
                    {metricText(value)}
                  </Descriptions.Item>
                ))
              ) : (
                <Descriptions.Item label={t('metrics')}>–</Descriptions.Item>
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
