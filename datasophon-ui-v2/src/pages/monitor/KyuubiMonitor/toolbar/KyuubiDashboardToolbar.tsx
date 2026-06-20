import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

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

function connTypeLabel(value: string): string {
  return value.replace('connection_total_', '');
}

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
}) => (
  <DashboardToolbar
    timeRange={timeRange}
    onTimeRangeChange={onTimeRangeChange}
    refreshInterval={refreshInterval}
    onRefreshIntervalChange={onRefreshIntervalChange}
    onRefresh={onRefresh}
  >
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
  </DashboardToolbar>
);

export default KyuubiDashboardToolbar;
