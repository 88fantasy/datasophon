import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

interface NginxDashboardToolbarProps {
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (value: string[]) => void;
  onRefresh: () => void;
}

const NginxDashboardToolbar: FC<NginxDashboardToolbarProps> = ({
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  instances,
  selectedInstances,
  onInstancesChange,
  onRefresh,
}) => {
  const intl = useIntl();

  return (
    <DashboardToolbar
      timeRange={timeRange}
      onTimeRangeChange={onTimeRangeChange}
      refreshInterval={refreshInterval}
      onRefreshIntervalChange={onRefreshIntervalChange}
      onRefresh={onRefresh}
    >
      <Select
        mode="multiple"
        placeholder={intl.formatMessage({
          id: 'pages.prometheusMonitor.toolbar.instance',
        })}
        value={selectedInstances}
        onChange={onInstancesChange}
        options={instances.map((value) => ({ label: value, value }))}
        style={{ minWidth: 210 }}
        maxTagCount="responsive"
      />
    </DashboardToolbar>
  );
};

export default NginxDashboardToolbar;
