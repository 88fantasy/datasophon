import { Button, Drawer, Empty, Spin } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';

import StatPanel from '../../monitor/_shared/panels/StatPanel';
import TimeSeriesPanel from '../../monitor/_shared/panels/TimeSeriesPanel';
import type { TimeSeriesPoint } from '../../monitor/_shared/types';
import { getServiceSummary, type ServiceSummary } from './service';
import { formatDuration } from './TraceDetailDrawer';

interface ServiceDetailDrawerProps {
  clusterId: number;
  serviceName?: string;
  open: boolean;
  timeRange: [Dayjs, Dayjs];
  onClose: () => void;
  onShowTraces: (serviceName: string) => void;
}

function toSeconds(value: Dayjs) {
  return Math.floor(value.valueOf() / 1000);
}

function ratio(current: number, previous: number) {
  if (previous <= 0) return Number.NaN;
  return (current - previous) / previous;
}

const ServiceDetailDrawer: React.FC<ServiceDetailDrawerProps> = ({
  clusterId,
  serviceName,
  open,
  timeRange,
  onClose,
  onShowTraces,
}) => {
  const [summary, setSummary] = useState<ServiceSummary>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !serviceName) return;
    const [start, end] = timeRange;
    setLoading(true);
    setSummary(undefined);
    getServiceSummary(clusterId, toSeconds(start), toSeconds(end), serviceName)
      .then((result) => {
        setSummary(result.data);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [clusterId, serviceName, open, timeRange]);

  const windowSeconds = Math.max(
    toSeconds(timeRange[1]) - toSeconds(timeRange[0]),
    1,
  );
  const current = summary?.current;
  const previous = summary?.previous;

  const qps = current ? current.spanCount / windowSeconds : Number.NaN;
  const previousQps = previous
    ? previous.spanCount / windowSeconds
    : Number.NaN;
  const errorRate =
    current && current.spanCount > 0
      ? (current.errorCount / current.spanCount) * 100
      : 0;
  const previousErrorRate =
    previous && previous.spanCount > 0
      ? (previous.errorCount / previous.spanCount) * 100
      : 0;

  const seriesData: TimeSeriesPoint[] = (summary?.series ?? []).flatMap(
    (point) => {
      const time = dayjs.utc(point.time).valueOf();
      return [
        { time, value: point.spanCount, series: '请求量' },
        { time, value: point.errorCount, series: '错误数' },
      ];
    },
  );

  return (
    <Drawer
      title={serviceName}
      placement="right"
      width={440}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={
        serviceName && (
          <Button size="small" onClick={() => onShowTraces(serviceName)}>
            查看 Traces
          </Button>
        )
      }
    >
      <Spin spinning={loading}>
        {!current ? (
          <Empty style={{ padding: '40px 0' }} description="暂无数据" />
        ) : (
          <>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 12,
                marginBottom: 16,
              }}
            >
              <StatPanel
                title="请求量"
                value={current.spanCount}
                changeRatio={ratio(current.spanCount, previous?.spanCount ?? 0)}
              />
              <StatPanel
                title="调用频率"
                value={qps}
                suffix="qps"
                precision={2}
                changeRatio={ratio(qps, previousQps)}
              />
              <StatPanel
                title="错误率"
                value={errorRate}
                suffix="%"
                precision={1}
                color={errorRate > 0 ? '#ff4d4f' : undefined}
                changeRatio={ratio(errorRate, previousErrorRate)}
              />
              <StatPanel
                title="平均响应耗时"
                value={current.avgDurationNs}
                formatter={formatDuration}
                changeRatio={ratio(
                  current.avgDurationNs,
                  previous?.avgDurationNs ?? 0,
                )}
              />
              <StatPanel
                title="P99 耗时"
                value={current.p99DurationNs}
                formatter={formatDuration}
                changeRatio={ratio(
                  current.p99DurationNs,
                  previous?.p99DurationNs ?? 0,
                )}
              />
              <StatPanel
                title="最大调用延时"
                value={current.maxDurationNs}
                formatter={formatDuration}
                changeRatio={ratio(
                  current.maxDurationNs,
                  previous?.maxDurationNs ?? 0,
                )}
              />
            </div>
            <TimeSeriesPanel title="调用概况" data={seriesData} height={200} />
          </>
        )}
      </Spin>
    </Drawer>
  );
};

export default ServiceDetailDrawer;
