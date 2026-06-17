import { ReloadOutlined } from '@ant-design/icons';
import { Button, Segmented, Select, Space, Tag, Tooltip } from 'antd';
import { type FC, useEffect, useRef, useState } from 'react';

export type TimeRange = '5m' | '15m' | '1h' | '6h' | '24h';
export type RefreshInterval = 'off' | '30s' | '1m';

interface DashboardToolbarProps {
  timeRange: TimeRange;
  onTimeRangeChange: (v: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (v: RefreshInterval) => void;
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (v: string[]) => void;
  services: string[];
  selectedServices: string[];
  onServicesChange: (v: string[]) => void;
  onRefresh: () => void;
}

const TIME_RANGE_OPTIONS: Array<{ label: string; value: TimeRange }> = [
  { label: 'Last 5m', value: '5m' },
  { label: 'Last 15m', value: '15m' },
  { label: 'Last 1h', value: '1h' },
  { label: 'Last 6h', value: '6h' },
  { label: 'Last 24h', value: '24h' },
];

const REFRESH_OPTIONS: Array<{ label: string; value: RefreshInterval }> = [
  { label: 'Off', value: 'off' },
  { label: '30s', value: '30s' },
  { label: '1m', value: '1m' },
];

function intervalToSeconds(interval: RefreshInterval): number {
  if (interval === '30s') return 30;
  if (interval === '1m') return 60;
  return 0;
}

const DashboardToolbar: FC<DashboardToolbarProps> = ({
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  instances,
  selectedInstances,
  onInstancesChange,
  services,
  selectedServices,
  onServicesChange,
  onRefresh,
}) => {
  const [countdown, setCountdown] = useState<number>(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    const secs = intervalToSeconds(refreshInterval);
    if (secs === 0) {
      setCountdown(0);
      return;
    }
    setCountdown(secs);
    timerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          onRefresh();
          return secs;
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
      {/* 实例 & 服务筛选 */}
      <Space wrap>
        <Select
          mode="multiple"
          placeholder="实例"
          value={selectedInstances}
          onChange={onInstancesChange}
          options={instances.map((v) => ({ label: v, value: v }))}
          style={{ minWidth: 180 }}
          maxTagCount="responsive"
        />
        <Select
          mode="multiple"
          placeholder="服务"
          value={selectedServices}
          onChange={onServicesChange}
          options={services.map((v) => ({ label: v, value: v }))}
          style={{ minWidth: 160 }}
          maxTagCount="responsive"
        />
      </Space>

      {/* 弹性空隙 */}
      <div className="flex-1" />

      {/* 时间范围 */}
      <Segmented
        options={TIME_RANGE_OPTIONS}
        value={timeRange}
        onChange={(v) => onTimeRangeChange(v as TimeRange)}
        size="small"
      />

      {/* 刷新间隔 */}
      <Select
        value={refreshInterval}
        onChange={onRefreshIntervalChange}
        options={REFRESH_OPTIONS}
        style={{ width: 80 }}
        size="small"
      />

      {/* 倒计时 + 手动刷新 */}
      <Tooltip title="立即刷新">
        <Button
          icon={<ReloadOutlined />}
          onClick={onRefresh}
          size="small"
          type={countdown > 0 ? 'primary' : 'default'}
          ghost={countdown > 0}
        >
          {countdown > 0 ? (
            <Tag color="processing" style={{ margin: 0, borderRadius: 8 }}>
              ⟳ {countdown}s
            </Tag>
          ) : null}
        </Button>
      </Tooltip>
    </div>
  );
};

export default DashboardToolbar;
