import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import type { FC } from 'react';
import type {
  RefreshInterval,
  TimeRange,
} from '../../_shared/DashboardToolbar';
import DashboardToolbar from '../../_shared/DashboardToolbar';

export type { RefreshInterval, TimeRange };

export type DorisRateInterval = '1m' | '2m' | '5m' | '10m';

interface DorisDashboardToolbarProps {
  hideClusterSelect?: boolean;
  cluster: string;
  clusters: string[];
  onClusterChange: (value: string) => void;
  feInstances: string[];
  selectedFeInstances: string[];
  onFeInstancesChange: (value: string[]) => void;
  beInstances: string[];
  selectedBeInstances: string[];
  onBeInstancesChange: (value: string[]) => void;
  rateInterval: DorisRateInterval;
  onRateIntervalChange: (value: DorisRateInterval) => void;
  timeRange: TimeRange;
  onTimeRangeChange: (value: TimeRange) => void;
  refreshInterval: RefreshInterval;
  onRefreshIntervalChange: (value: RefreshInterval) => void;
  onRefresh: () => void;
}

const RATE_OPTIONS: Array<{ label: string; value: DorisRateInterval }> = [
  { label: '1m', value: '1m' },
  { label: '2m', value: '2m' },
  { label: '5m', value: '5m' },
  { label: '10m', value: '10m' },
];

const DorisDashboardToolbar: FC<DorisDashboardToolbarProps> = ({
  hideClusterSelect = false,
  cluster,
  clusters,
  onClusterChange,
  feInstances,
  selectedFeInstances,
  onFeInstancesChange,
  beInstances,
  selectedBeInstances,
  onBeInstancesChange,
  rateInterval,
  onRateIntervalChange,
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
      {!hideClusterSelect && (
        <Select
          value={cluster}
          onChange={onClusterChange}
          options={(clusters.length > 0 ? clusters : [cluster]).map(
            (value) => ({
              label: value,
              value,
            }),
          )}
          style={{ minWidth: 180 }}
          aria-label={t('pages.dorisMonitor.toolbar.cluster')}
        />
      )}
      <Select
        mode="multiple"
        placeholder={t('pages.dorisMonitor.toolbar.feInstance')}
        value={selectedFeInstances}
        onChange={onFeInstancesChange}
        options={feInstances.map((value) => ({ label: value, value }))}
        style={{ minWidth: 240 }}
        maxTagCount="responsive"
      />
      <Select
        mode="multiple"
        placeholder={t('pages.dorisMonitor.toolbar.beInstance')}
        value={selectedBeInstances}
        onChange={onBeInstancesChange}
        options={beInstances.map((value) => ({ label: value, value }))}
        style={{ minWidth: 240 }}
        maxTagCount="responsive"
      />
      <Select
        value={rateInterval}
        onChange={onRateIntervalChange}
        options={RATE_OPTIONS}
        style={{ width: 96 }}
        aria-label={t('pages.dorisMonitor.toolbar.rateInterval')}
      />
    </DashboardToolbar>
  );
};

export default DorisDashboardToolbar;
