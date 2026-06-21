import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

interface JuiceFSDashboardToolbarProps {
  volume: string;
  volumes: string[];
  onVolumeChange: (value: string) => void;
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  onRefresh: () => void;
}

const JuiceFSDashboardToolbar: FC<JuiceFSDashboardToolbarProps> = ({
  volume,
  volumes,
  onVolumeChange,
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  onRefresh,
}) => {
  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });

  return (
    <DashboardToolbar
      timeRange={timeRange}
      onTimeRangeChange={onTimeRangeChange}
      refreshInterval={refreshInterval}
      onRefreshIntervalChange={onRefreshIntervalChange}
      onRefresh={onRefresh}
    >
      <Select
        value={volume}
        onChange={onVolumeChange}
        options={volumes.map((v) => ({ label: v, value: v }))}
        placeholder={t('pages.juiceFSMonitor.toolbar.volume')}
        style={{ minWidth: 180 }}
      />
    </DashboardToolbar>
  );
};

export default JuiceFSDashboardToolbar;
