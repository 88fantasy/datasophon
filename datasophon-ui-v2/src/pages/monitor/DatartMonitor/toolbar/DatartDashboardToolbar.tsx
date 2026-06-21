import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

interface DatartDashboardToolbarProps {
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  applications: string[];
  selectedApplication: string;
  onApplicationChange: (value: string) => void;
  instances: string[];
  selectedInstances: string[];
  onInstancesChange: (value: string[]) => void;
  heapPools: string[];
  selectedHeapPool: string;
  onHeapPoolChange: (value: string) => void;
  hikaricpPools: string[];
  selectedHikaricpPool: string;
  onHikaricpPoolChange: (value: string) => void;
  onRefresh: () => void;
}

const DatartDashboardToolbar: FC<DatartDashboardToolbarProps> = ({
  timeRange,
  onTimeRangeChange,
  refreshInterval,
  onRefreshIntervalChange,
  applications,
  selectedApplication,
  onApplicationChange,
  instances,
  selectedInstances,
  onInstancesChange,
  heapPools,
  selectedHeapPool,
  onHeapPoolChange,
  hikaricpPools,
  selectedHikaricpPool,
  onHikaricpPoolChange,
  onRefresh,
}) => (
  <DashboardToolbar
    timeRange={timeRange}
    onTimeRangeChange={onTimeRangeChange}
    refreshInterval={refreshInterval}
    onRefreshIntervalChange={onRefreshIntervalChange}
    onRefresh={onRefresh}
  >
    <Select
      placeholder="Application"
      value={selectedApplication}
      onChange={onApplicationChange}
      options={applications.map((value) => ({ label: value, value }))}
      style={{ minWidth: 140 }}
    />
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
      placeholder="Heap Pool"
      value={selectedHeapPool}
      onChange={onHeapPoolChange}
      options={heapPools.map((value) => ({ label: value, value }))}
      style={{ minWidth: 180 }}
    />
    <Select
      placeholder="HikariCP"
      value={selectedHikaricpPool}
      onChange={onHikaricpPoolChange}
      options={hikaricpPools.map((value) => ({ label: value, value }))}
      style={{ minWidth: 150 }}
    />
  </DashboardToolbar>
);

export default DatartDashboardToolbar;
