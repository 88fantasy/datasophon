import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

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

  return (
    <DashboardToolbar
      timeRange={timeRange}
      onTimeRangeChange={onTimeRangeChange}
      refreshInterval={refreshInterval}
      onRefreshIntervalChange={onRefreshIntervalChange}
      onRefresh={onRefresh}
    >
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
    </DashboardToolbar>
  );
};

export default DSDashboardToolbar;
