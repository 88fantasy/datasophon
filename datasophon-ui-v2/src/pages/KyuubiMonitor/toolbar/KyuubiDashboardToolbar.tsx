import { ReloadOutlined } from '@ant-design/icons';
import { Button, Segmented, Select, Space, Tag, Tooltip } from 'antd';
import { type FC, useEffect, useRef, useState } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../PrometheusMonitor/toolbar/DashboardToolbar';

interface KyuubiDashboardToolbarProps {
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (value: string[]) => void;
  connTypes: string[];
  selectedConnType: string;
  onConnTypeChange: (value: string) => void;
  opTypes: string[];
  selectedOpType: string;
  onOpTypeChange: (value: string) => void;
  onRefresh: () => void;
}

function intervalToSeconds(interval: RefreshInterval): number {
  if (interval === '30s') return 30;
  if (interval === '1m') return 60;
  return 0;
}

function connTypeLabel(value: string): string {
  return value.replace('connection_total_', '');
}

const timeRangeOptions: Array<{ label: string; value: TimeRange }> = [
  { label: 'Last 5m', value: '5m' },
  { label: 'Last 15m', value: '15m' },
  { label: 'Last 1h', value: '1h' },
  { label: 'Last 6h', value: '6h' },
  { label: 'Last 24h', value: '24h' },
  { label: 'Last 7d', value: '7d' },
];

const refreshOptions: Array<{ label: string; value: RefreshInterval }> = [
  { label: 'Off', value: 'off' },
  { label: '30s', value: '30s' },
  { label: '1m', value: '1m' },
];

const KyuubiDashboardToolbar: FC<KyuubiDashboardToolbarProps> = ({
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  instances,
  selectedInstances,
  onInstancesChange,
  connTypes,
  selectedConnType,
  onConnTypeChange,
  opTypes,
  selectedOpType,
  onOpTypeChange,
  onRefresh,
}) => {
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
          mode="multiple"
          placeholder="Instance"
          value={selectedInstances}
          onChange={onInstancesChange}
          options={instances.map((value) => ({ label: value, value }))}
          style={{ minWidth: 220 }}
          maxTagCount="responsive"
        />
        <Select
          value={selectedConnType}
          onChange={onConnTypeChange}
          options={connTypes.map((value) => ({
            label: connTypeLabel(value),
            value,
          }))}
          style={{ width: 150 }}
        />
        <Select
          value={selectedOpType}
          onChange={onOpTypeChange}
          options={opTypes.map((value) => ({ label: value, value }))}
          style={{ width: 190 }}
        />
      </Space>

      <div className="flex-1" />

      <Segmented
        options={timeRangeOptions}
        value={timeRange}
        onChange={(value) => onTimeRangeChange(value as TimeRange)}
        size="small"
      />

      <Select
        value={refreshInterval}
        onChange={onRefreshIntervalChange}
        options={refreshOptions}
        style={{ width: 80 }}
        size="small"
      />

      <Tooltip title="Refresh now">
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

export default KyuubiDashboardToolbar;
