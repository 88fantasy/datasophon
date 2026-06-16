import { ReloadOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Button, Segmented, Select, Space, Tag, Tooltip } from 'antd';
import { type FC, useEffect, useRef, useState } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../PrometheusMonitor/toolbar/DashboardToolbar';

export type DSApplication =
  | 'master-server'
  | 'worker-server'
  | 'api-server'
  | 'alert-server';

interface DSDashboardToolbarProps {
  application: DSApplication;
  onApplicationChange: (value: DSApplication) => void;
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (value: string[]) => void;
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  onRefresh: () => void;
}

const APPLICATION_OPTIONS: Array<{ label: string; value: DSApplication }> = [
  { label: 'master-server', value: 'master-server' },
  { label: 'worker-server', value: 'worker-server' },
  { label: 'api-server', value: 'api-server' },
  { label: 'alert-server', value: 'alert-server' },
];

function intervalToSeconds(interval: RefreshInterval): number {
  if (interval === '30s') return 30;
  if (interval === '1m') return 60;
  return 0;
}

const DSDashboardToolbar: FC<DSDashboardToolbarProps> = ({
  application,
  onApplicationChange,
  instances,
  selectedInstances,
  onInstancesChange,
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  onRefresh,
}) => {
  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });

  const TIME_RANGE_OPTIONS: Array<{ label: string; value: TimeRange }> = [
    { label: t('pages.prometheusMonitor.toolbar.timeRange.last5m'), value: '5m' },
    { label: t('pages.prometheusMonitor.toolbar.timeRange.last15m'), value: '15m' },
    { label: t('pages.prometheusMonitor.toolbar.timeRange.last1h'), value: '1h' },
    { label: t('pages.prometheusMonitor.toolbar.timeRange.last6h'), value: '6h' },
    { label: t('pages.prometheusMonitor.toolbar.timeRange.last24h'), value: '24h' },
    { label: t('pages.prometheusMonitor.toolbar.timeRange.last7d'), value: '7d' },
  ];

  const REFRESH_OPTIONS: Array<{ label: string; value: RefreshInterval }> = [
    { label: t('pages.prometheusMonitor.toolbar.refresh.off'), value: 'off' },
    { label: t('pages.prometheusMonitor.toolbar.refresh.30s'), value: '30s' },
    { label: t('pages.prometheusMonitor.toolbar.refresh.1m'), value: '1m' },
  ];

  const [countdown, setCountdown] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (timerRef.current) clearInterval(timerRef.current);

    const seconds = intervalToSeconds(refreshInterval);
    if (seconds === 0) {
      setCountdown(0);
      return;
    }

    setCountdown(seconds);
    timerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          onRefresh();
          return seconds;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [refreshInterval, onRefresh]);

  return (
    <div
      className="flex flex-wrap items-center gap-2 mb-4 px-1"
      style={{ borderBottom: '1px solid #f0f0f0', paddingBottom: 12 }}
    >
      <Space wrap>
        <Select
          value={application}
          onChange={onApplicationChange}
          options={APPLICATION_OPTIONS}
          style={{ minWidth: 180 }}
          aria-label={t('pages.dolphinSchedulerMonitor.toolbar.application')}
        />
        <Select
          mode="multiple"
          placeholder={t('pages.dolphinSchedulerMonitor.toolbar.instance')}
          value={selectedInstances}
          onChange={onInstancesChange}
          options={instances.map((value) => ({ label: value, value }))}
          style={{ minWidth: 240 }}
          maxTagCount="responsive"
        />
      </Space>

      <div className="flex-1" />

      <Segmented
        options={TIME_RANGE_OPTIONS}
        value={timeRange}
        onChange={(value) => onTimeRangeChange(value as TimeRange)}
        size="small"
      />

      <Select
        value={refreshInterval}
        onChange={onRefreshIntervalChange}
        options={REFRESH_OPTIONS}
        style={{ width: 80 }}
        size="small"
      />

      <Tooltip title={t('pages.prometheusMonitor.toolbar.refreshNow')}>
        <Button
          icon={<ReloadOutlined />}
          onClick={onRefresh}
          size="small"
          type={countdown > 0 ? 'primary' : 'default'}
          ghost={countdown > 0}
        >
          {countdown > 0 ? (
            <Tag color="processing" style={{ margin: 0, borderRadius: 8 }}>
              {countdown}s
            </Tag>
          ) : null}
        </Button>
      </Tooltip>
    </div>
  );
};

export default DSDashboardToolbar;
