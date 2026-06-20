import { useIntl } from '@umijs/max';
import { Badge, Row, Statistic } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import {
  CHART_COLORS,
  colorByThreshold,
  formatBytes,
} from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import MonitorPanelCard from '../_shared/MonitorPanelCard';
import PanelCol from '../_shared/PanelCol';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import ZKDashboardToolbar from '../ZooKeeperMonitor/toolbar/ZKDashboardToolbar';
import { useNexusDashboard } from './hooks/useNexusDashboard';

const STATUS_CODE_COLORS = {
  '1xx': '#8c8c8c',
  '2xx': CHART_COLORS.success,
  '3xx': CHART_COLORS.primary,
  '4xx': CHART_COLORS.warning,
  '5xx': CHART_COLORS.error,
};

const jvmPoolColors: Record<string, string> = {
  Eden: '#69b1ff',
  Survivor: '#95de64',
  'Old Gen': '#1677ff',
  Metaspace: '#faad14',
  'Code Cache': '#722ed1',
};

const latencyColors = {
  p50: CHART_COLORS.primary,
  p99: CHART_COLORS.error,
};

const heapColors = {
  Max: '#8c8c8c',
  Used: CHART_COLORS.primary,
  Committed: CHART_COLORS.warning,
};

const gcColors = {
  MarkSweep: CHART_COLORS.error,
  Scavenge: CHART_COLORS.primary,
};

const threadStateColors = {
  Runnable: CHART_COLORS.success,
  Blocked: CHART_COLORS.error,
  Waiting: '#8c8c8c',
  'Timed Waiting': CHART_COLORS.primary,
};

const jettyThreadPoolColors = {
  'Queued Jobs': CHART_COLORS.warning,
  'Pool Size': CHART_COLORS.primary,
};

const bufferColors = {
  'Non-Heap': CHART_COLORS.primary,
  'Direct Buffers': CHART_COLORS.warning,
  'Mapped Buffers': CHART_COLORS.success,
};

const integerFormatter = (value: number) => value.toFixed(0);
const millisecondFormatter = (value: number) => `${value.toFixed(1)}ms`;
const opsFormatter = (value: number) => `${value.toFixed(3)}/s`;

function formatDuration(ms: number): string {
  const totalMinutes = Math.floor(ms / 60000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${Math.floor(ms / 1000)}s`;
}

interface StatusStatPanelProps {
  title: string;
  value: number;
}

const StatusStatPanel: FC<StatusStatPanelProps> = ({ title, value }) => {
  const readOnly = value >= 1;
  const color = readOnly ? CHART_COLORS.error : CHART_COLORS.success;

  return (
    <MonitorPanelCard compact>
      <Statistic
        title={title}
        value={value}
        formatter={() => (
          <Badge
            status={readOnly ? 'error' : 'success'}
            text={readOnly ? 'Read-Only' : 'Read / Write'}
          />
        )}
        styles={{
          content: { color, fontSize: 24, fontWeight: 600 },
          value: { color },
        }}
      />
    </MonitorPanelCard>
  );
};

const NexusDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const title = intl.formatMessage({
    id: 'pages.nexusMonitor.title',
    defaultMessage: 'Nexus Monitor',
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

  const { instant, series, instances, jobs, loading } = useNexusDashboard({
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
            color={colorByThreshold(instant.uptime, [300_000, 300_000], {
              reverse: true,
            })}
            formatter={formatDuration}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Heap Ratio"
            value={instant.heapRatio}
            color={colorByThreshold(instant.heapRatio, [80, 90])}
            suffix="%"
            precision={1}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="FileDescriptor Ratio"
            value={instant.fdRatio}
            color={colorByThreshold(instant.fdRatio, [80, 90])}
            suffix="%"
            precision={1}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatusStatPanel
            title="Readonly Enabled"
            value={instant.readonlyEnabled}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="JVM Threads"
            value={instant.jvmThreads}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Deadlock Threads"
            value={instant.deadlockThreads}
            color={
              instant.deadlockThreads === 0
                ? CHART_COLORS.success
                : CHART_COLORS.error
            }
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="Jetty Responses by Code"
            data={series.N07}
            yFormatter={opsFormatter}
            colorMap={STATUS_CODE_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="Component Exceptions"
            data={series.N08}
            yFormatter={opsFormatter}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Jetty Request Latency"
            data={series.N09}
            yFormatter={millisecondFormatter}
            colorMap={latencyColors}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Component Read Latency (p99)"
            data={series.N10}
            yFormatter={millisecondFormatter}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="FileBlobStore Op Latency (p99)"
            data={series.N11}
            yFormatter={millisecondFormatter}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="JVM Heap"
            data={series.N12}
            yFormatter={formatBytes}
            colorMap={heapColors}
          />
        </PanelCol>
        <PanelCol span={12}>
          <AreaPanel
            title="JVM Memory Pools (used)"
            data={series.N13}
            stack
            yFormatter={formatBytes}
            colorMap={jvmPoolColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="GC Collection Rate"
            data={series.N14}
            yFormatter={opsFormatter}
            colorMap={gcColors}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="GC Pause Durations"
            data={series.N15}
            yFormatter={millisecondFormatter}
            colorMap={gcColors}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="Thread States"
            data={series.N16}
            yFormatter={integerFormatter}
            colorMap={threadStateColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="Jetty ThreadPool"
            data={series.N17}
            yFormatter={integerFormatter}
            colorMap={jettyThreadPoolColors}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="Non-Heap & Buffers"
            data={series.N18}
            yFormatter={formatBytes}
            colorMap={bufferColors}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default NexusDashboard;
