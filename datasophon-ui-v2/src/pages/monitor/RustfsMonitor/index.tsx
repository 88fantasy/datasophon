import { useIntl } from '@umijs/max';
import { Row } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import { CHART_COLORS, formatBytes } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import PanelCol from '../_shared/PanelCol';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import ZKDashboardToolbar from '../ZooKeeperMonitor/toolbar/ZKDashboardToolbar';
import { useRustfsDashboard } from './hooks/useRustfsDashboard';

const latencyColors = {
  p50: CHART_COLORS.primary,
  p99: CHART_COLORS.error,
};

const trafficColors = {
  Request: CHART_COLORS.primary,
  Response: CHART_COLORS.success,
};

const errorColors = {
  'I/O': CHART_COLORS.error,
  Timeout: CHART_COLORS.warning,
  Availability: '#722ed1',
};

const capacityColors = {
  Used: CHART_COLORS.primary,
  Total: '#8c8c8c',
};

const fdColors = {
  Open: CHART_COLORS.primary,
  Limit: '#8c8c8c',
};

const percentFormatter = (value: number) => `${value.toFixed(1)}%`;
const opsFormatter = (value: number) => `${value.toFixed(2)}/s`;
const bpsFormatter = (value: number) => `${formatBytes(value)}/s`;
const secondsFormatter = (value: number) => `${(value * 1000).toFixed(0)}ms`;
const integerFormatter = (value: number) => value.toFixed(0);

function formatDuration(seconds: number): string {
  const totalMinutes = Math.floor(seconds / 60);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${Math.floor(seconds)}s`;
}

const RustfsDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const title = intl.formatMessage({
    id: 'pages.rustfsMonitor.title',
    defaultMessage: 'RustFS Monitor',
  });

  const variables = useMemo(
    () => ({
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
      job: selectedJobs.length > 0 ? selectionsToRegex(selectedJobs) : '.+',
    }),
    [selectedInstances, selectedJobs],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, instances, jobs, loading } = useRustfsDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={title}
      toolbar={
        <ZKDashboardToolbar
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          instances={instances}
          selectedInstances={selectedInstances}
          onInstancesChange={setSelectedInstances}
          jobs={jobs}
          selectedJobs={selectedJobs}
          onJobsChange={setSelectedJobs}
          onRefresh={handleRefresh}
        />
      }
      meta={
        <>
          {'instance=~"'}
          {variables.instance}
          {'" job=~"'}
          {variables.job}
          {'"'}
          {' · '}
          range={timeRange}
        </>
      }
      loading={loading}
    >
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={4}>
          <StatPanel
            title="Uptime"
            value={instant.uptime}
            color={CHART_COLORS.primary}
            formatter={formatDuration}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Buckets"
            value={instant.buckets}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Objects"
            value={instant.objects}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Drives Online"
            value={instant.drivesOnline}
            color={CHART_COLORS.success}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Drives Offline"
            value={instant.drivesOffline}
            color={
              instant.drivesOffline === 0
                ? CHART_COLORS.success
                : CHART_COLORS.error
            }
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="S3 Operations by API"
            data={series.R06}
            yFormatter={opsFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="HTTP Requests by Status"
            data={series.R07}
            yFormatter={opsFormatter}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="HTTP Traffic"
            data={series.R08}
            yFormatter={bpsFormatter}
            colorMap={trafficColors}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="HTTP Request Duration"
            data={series.R09}
            yFormatter={secondsFormatter}
            colorMap={latencyColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="HTTP Failures"
            data={series.R10}
            yFormatter={opsFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="Drive I/O Errors"
            data={series.R11}
            yFormatter={opsFormatter}
            colorMap={errorColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Capacity Used %"
            data={series.R12}
            yFormatter={percentFormatter}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Process CPU %"
            data={series.R13}
            yFormatter={percentFormatter}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Process Memory"
            data={series.R14}
            yFormatter={formatBytes}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Drive Capacity by Drive"
            data={series.R15}
            yFormatter={formatBytes}
            colorMap={capacityColors}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Drive IOPS by Drive"
            data={series.R16}
            yFormatter={integerFormatter}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="File Descriptors"
            data={series.R17}
            yFormatter={integerFormatter}
            colorMap={fdColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={24}>
          <TimeSeriesPanel
            title="Replication Active Workers"
            data={series.R18}
            yFormatter={integerFormatter}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default RustfsDashboard;
