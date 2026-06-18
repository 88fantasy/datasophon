import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

interface DSDashboardToolbarProps {
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (value: string[]) => void;
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  onRefresh: () => void;
}

const DSDashboardToolbar: FC<DSDashboardToolbarProps> = ({
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
        placeholder={t('pages.dolphinSchedulerMonitor.toolbar.instance')}
        value={selectedInstances}
        onChange={onInstancesChange}
        options={instances.map((value) => ({ label: value, value }))}
        style={{ minWidth: 240 }}
        maxTagCount="responsive"
      />
    </DashboardToolbar>
  );
};

export default DSDashboardToolbar;
