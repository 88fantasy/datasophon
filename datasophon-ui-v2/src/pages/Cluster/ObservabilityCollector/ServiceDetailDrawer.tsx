import { useIntl } from '@umijs/max';
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
  const intl = useIntl();
  const t = (id: string, defaultMessage: string) =>
    intl.formatMessage({ id, defaultMessage });
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

  const requestCountLabel = t(
    'pages.observabilityCollector.requestCount',
    'Request count',
  );
  const errorCountLabel = t(
    'pages.observabilityCollector.errorCountMetric',
    'Error count',
  );
  const seriesData: TimeSeriesPoint[] = (summary?.series ?? []).flatMap(
    (point) => {
      const time = dayjs.utc(point.time).valueOf();
      return [
        { time, value: point.spanCount, series: requestCountLabel },
        { time, value: point.errorCount, series: errorCountLabel },
      ];
    },
  );

  return (
    <Drawer
      title={serviceName}
      placement="right"
      size={440}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={
        serviceName && (
          <Button size="small" onClick={() => onShowTraces(serviceName)}>
            {t('pages.observabilityCollector.viewTraces', 'View traces')}
          </Button>
        )
      }
    >
      <Spin spinning={loading}>
        {!current ? (
          <Empty
            style={{ padding: '40px 0' }}
            description={t('pages.observabilityCollector.noData', 'No data')}
          />
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
                title={requestCountLabel}
                value={current.spanCount}
                changeRatio={ratio(current.spanCount, previous?.spanCount ?? 0)}
              />
              <StatPanel
                title={t('pages.observabilityCollector.callRate', 'Call rate')}
                value={qps}
                suffix="qps"
                precision={2}
                changeRatio={ratio(qps, previousQps)}
              />
              <StatPanel
                title={t(
                  'pages.observabilityCollector.errorRate',
                  'Error rate',
                )}
                value={errorRate}
                suffix="%"
                precision={1}
                color={errorRate > 0 ? '#ff4d4f' : undefined}
                changeRatio={ratio(errorRate, previousErrorRate)}
              />
              <StatPanel
                title={t(
                  'pages.observabilityCollector.avgResponseTime',
                  'Avg response time',
                )}
                value={current.avgDurationNs}
                formatter={formatDuration}
                changeRatio={ratio(
                  current.avgDurationNs,
                  previous?.avgDurationNs ?? 0,
                )}
              />
              <StatPanel
                title={t(
                  'pages.observabilityCollector.p99Duration',
                  'P99 duration',
                )}
                value={current.p99DurationNs}
                formatter={formatDuration}
                changeRatio={ratio(
                  current.p99DurationNs,
                  previous?.p99DurationNs ?? 0,
                )}
              />
              <StatPanel
                title={t(
                  'pages.observabilityCollector.maxLatency',
                  'Max latency',
                )}
                value={current.maxDurationNs}
                formatter={formatDuration}
                changeRatio={ratio(
                  current.maxDurationNs,
                  previous?.maxDurationNs ?? 0,
                )}
              />
            </div>
            <TimeSeriesPanel
              title={t(
                'pages.observabilityCollector.callOverview',
                'Call overview',
              )}
              data={seriesData}
              height={200}
            />
          </>
        )}
      </Spin>
    </Drawer>
  );
};

export default ServiceDetailDrawer;
