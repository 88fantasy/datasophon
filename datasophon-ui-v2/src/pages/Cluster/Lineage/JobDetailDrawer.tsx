import { Line } from '@ant-design/plots';
import { useIntl } from '@umijs/max';
import { Descriptions, Drawer, Empty, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  formatBytes,
  formatRecordsRate,
  formatRowCount,
  formatRunAt,
} from './lineageFormatters';
import { getJob, getJobRateHistory } from './service';
import type { GraphJob, JobDetail, JobRatePoint } from './service';

interface JobDetailDrawerProps {
  clusterId: number;
  jobs: GraphJob[];
  open: boolean;
  onClose: () => void;
}

const JobDetailDrawer: React.FC<JobDetailDrawerProps> = ({
  clusterId,
  jobs,
  open,
  onClose,
}) => {
  const intl = useIntl();
  const t = (id: string, defaultMessage: string) =>
    intl.formatMessage({ id, defaultMessage });
  const [details, setDetails] = useState<Map<number, JobDetail>>(new Map());
  const [loading, setLoading] = useState(false);
  const [rateHistory, setRateHistory] = useState<Map<string, JobRatePoint[]>>(
    new Map(),
  );
  const [rateLoading, setRateLoading] = useState(false);

  useEffect(() => {
    if (!open || jobs.length === 0) return;
    const uniqueJobIds = Array.from(new Set(jobs.map((job) => job.jobId)));
    setLoading(true);
    Promise.all(
      uniqueJobIds.map((jobId) =>
        getJob(clusterId, jobId, { skipErrorHandler: true })
          .then((detail) => [jobId, detail] as const)
          .catch(() => null),
      ),
    )
      .then((results) => {
        const map = new Map<number, JobDetail>();
        results.forEach((entry) => {
          if (entry) map.set(entry[0], entry[1]);
        });
        setDetails(map);
      })
      .finally(() => setLoading(false));
  }, [open, jobs, clusterId]);

  useEffect(() => {
    if (!open) return;
    const appIds = Array.from(
      new Set(
        jobs.flatMap((job) =>
          job.runningAppId ? [job.runningAppId] : [],
        ),
      ),
    );
    if (appIds.length === 0) {
      setRateHistory(new Map());
      setRateLoading(false);
      return;
    }

    let cancelled = false;
    setRateLoading(true);
    Promise.all(
      appIds.map((appId) =>
        getJobRateHistory(clusterId, appId)
          .then((points) => [appId, points] as const)
          .catch(() => [appId, [] as JobRatePoint[]] as const),
      ),
    )
      .then((entries) => {
        if (!cancelled) setRateHistory(new Map(entries));
      })
      .finally(() => {
        if (!cancelled) setRateLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, jobs, clusterId]);

  return (
    <Drawer
      title={t('pages.lineage.jobDrawer.title', '关联作业')}
      open={open}
      onClose={onClose}
      size={480}
    >
      <Spin spinning={loading}>
        {jobs.length === 0 ? (
          <Empty description={t('pages.lineage.jobDrawer.empty', '此边未关联任何作业')} />
        ) : (
          jobs.map((job) => {
            const detail = details.get(job.jobId);
            return (
              <div key={job.edgeId} style={{ marginBottom: 20 }}>
                <Typography.Text strong>
                  {detail?.jobName ?? job.jobName}
                </Typography.Text>{' '}
                <Tag>{job.flowType}</Tag>
                <Descriptions
                  column={1}
                  size="small"
                  style={{ marginTop: 8 }}
                  title={t(
                    'pages.lineage.jobDrawer.latestStatistics',
                    '最近运行统计',
                  )}
                >
                  <Descriptions.Item
                    label={t('pages.lineage.jobDrawer.rowCount', '写入行数')}
                  >
                    {formatRowCount(job.lastRowCount)}
                  </Descriptions.Item>
                  <Descriptions.Item
                    label={t('pages.lineage.jobDrawer.bytes', '写入字节')}
                  >
                    {formatBytes(job.lastBytes)}
                  </Descriptions.Item>
                  <Descriptions.Item
                    label={t('pages.lineage.jobDrawer.lastRunAt', '最近运行时间')}
                  >
                    {formatRunAt(job.lastRunAt)}
                  </Descriptions.Item>
                </Descriptions>
                {job.runningAppId && (
                  <div style={{ marginTop: 12 }}>
                    <Typography.Title level={5}>
                      {t(
                        'pages.lineage.jobDrawer.recordsWrittenRate',
                        '写入速率（近 1 小时）',
                      )}
                    </Typography.Title>
                    <Spin spinning={rateLoading}>
                      {(rateHistory.get(job.runningAppId) ?? []).length > 0 ? (
                        <Line
                          data={rateHistory.get(job.runningAppId) ?? []}
                          xField="time"
                          yField="value"
                          height={180}
                          smooth
                          axis={{
                            x: { type: 'time', title: false },
                            y: {
                              title: false,
                              labelFormatter: (value: number) =>
                                formatRecordsRate(value),
                            },
                          }}
                        />
                      ) : (
                        !rateLoading && (
                          <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={t(
                              'pages.lineage.jobDrawer.rateEmpty',
                              '暂无速率数据',
                            )}
                          />
                        )
                      )}
                    </Spin>
                  </div>
                )}
                {detail ? (
                  <Descriptions
                    column={1}
                    size="small"
                    style={{ marginTop: 8 }}
                    title={t('pages.lineage.jobDrawer.jobInfo', '作业信息')}
                  >
                    <Descriptions.Item label={t('pages.lineage.jobDrawer.engine', '引擎')}>
                      {detail.engine}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages.lineage.jobDrawer.jobType', '类型')}>
                      {detail.jobType}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages.lineage.jobDrawer.state', '状态')}>
                      {detail.state}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages.lineage.jobDrawer.owner', 'Owner')}>
                      {detail.owner ?? '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages.lineage.jobDrawer.updateTime', '更新时间')}>
                      {detail.updateTime}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages.lineage.jobDrawer.externalUrl', '外部链接')}>
                      {detail.externalUrl ? (
                        <a href={detail.externalUrl} target="_blank" rel="noreferrer">
                          {detail.externalUrl}
                        </a>
                      ) : (
                        '-'
                      )}
                    </Descriptions.Item>
                  </Descriptions>
                ) : (
                  !loading && (
                    <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                      {t('pages.lineage.jobDrawer.detailUnavailable', '作业详情加载失败')}
                    </Typography.Text>
                  )
                )}
              </div>
            );
          })
        )}
      </Spin>
    </Drawer>
  );
};

export default JobDetailDrawer;
