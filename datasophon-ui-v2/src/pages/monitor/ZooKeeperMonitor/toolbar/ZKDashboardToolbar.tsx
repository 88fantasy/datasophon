import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

interface ZKDashboardToolbarProps {
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (value: string[]) => void;
  jobs: string[];
  selectedJobs: string[];
  onJobsChange: (value: string[]) => void;
  onRefresh: () => void;
}

const ZKDashboardToolbar: FC<ZKDashboardToolbarProps> = ({
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  instances,
  selectedInstances,
  onInstancesChange,
  jobs,
  selectedJobs,
  onJobsChange,
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
        placeholder={t('pages.prometheusMonitor.toolbar.instance')}
        value={selectedInstances}
        onChange={onInstancesChange}
        options={instances.map((value) => ({ label: value, value }))}
        style={{ minWidth: 210 }}
        maxTagCount="responsive"
      />
      <Select
        mode="multiple"
        placeholder={t('pages.zookeeperMonitor.toolbar.job')}
        value={selectedJobs}
        onChange={onJobsChange}
        options={jobs.map((value) => ({ label: value, value }))}
        style={{ minWidth: 190 }}
        maxTagCount="responsive"
      />
    </DashboardToolbar>
  );
};

export default ZKDashboardToolbar;
