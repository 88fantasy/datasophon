import { useIntl } from '@umijs/max';
import { Descriptions, Drawer, Empty, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { getJob } from './service';
import type { GraphJob, JobDetail } from './service';

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
                  {detail?.jobName ?? `job#${job.jobId}`}
                </Typography.Text>{' '}
                <Tag>{job.flowType}</Tag>
                {detail ? (
                  <Descriptions column={1} size="small" style={{ marginTop: 8 }}>
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
