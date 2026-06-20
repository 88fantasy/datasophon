import { useIntl } from '@umijs/max';
import { Row } from 'antd';
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
import PanelCol from '../_shared/PanelCol';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import type { TimeSeriesPoint } from '../_shared/types';
import ZKDashboardToolbar from '../ZooKeeperMonitor/toolbar/ZKDashboardToolbar';
import { useMySQLDashboard } from './hooks/useMySQLDashboard';

const networkColors = {
  Inbound: CHART_COLORS.success,
  Outbound: CHART_COLORS.primary,
};

const connectionColors = {
  Connections: CHART_COLORS.primary,
  'Max Used': CHART_COLORS.warning,
  'Max Connections': CHART_COLORS.error,
};

const threadColors = {
  'Threads Connected': CHART_COLORS.primary,
  'Threads Running': CHART_COLORS.warning,
};

const abortedColors = {
  'Aborted Connects': '#ff7a45',
  'Aborted Clients': CHART_COLORS.error,
};

const tableLockColors = {
  Immediate: CHART_COLORS.success,
  Waited: CHART_COLORS.error,
};

const memoryColors = {
  'Buffer Pool Data': CHART_COLORS.primary,
  'Log Buffer': CHART_COLORS.warning,
  'Key Buffer': CHART_COLORS.success,
  'Adaptive Hash': '#13c2c2',
};

const tempObjectColors = {
  'Tmp Tables': CHART_COLORS.primary,
  'Tmp Disk Tables': CHART_COLORS.error,
  'Tmp Files': CHART_COLORS.warning,
};

const handlerColors = {
  read_rnd_next: CHART_COLORS.primary,
  write: CHART_COLORS.warning,
  update: CHART_COLORS.success,
  delete: CHART_COLORS.error,
  read_key: '#13c2c2',
};

const commandColors = {
  select: CHART_COLORS.primary,
  insert: CHART_COLORS.success,
  update: CHART_COLORS.warning,
  set_option: '#13c2c2',
  commit: '#722ed1',
};

const integerFormatter = (value: number) => value.toFixed(0);
const qpsFormatter = (value: number) => value.toFixed(1);
const perSecondFormatter = (value: number) => `${value.toFixed(2)}/s`;
const bytesPerSecondFormatter = (value: number) => `${formatBytes(value, 1)}/s`;

function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

function hideZeroSeries(data: TimeSeriesPoint[]): TimeSeriesPoint[] {
  const totals = new Map<string, number>();
  for (const point of data) {
    totals.set(
      point.series,
      (totals.get(point.series) ?? 0) + Math.abs(point.value),
    );
  }

  return data.filter((point) => (totals.get(point.series) ?? 0) > 0);
}

function maxSeriesValue(data: TimeSeriesPoint[], series: string) {
  const values = data
    .filter((point) => point.series === series)
    .map((point) => point.value);
  return values.length > 0 ? Math.max(...values) : 0;
}

const MySQLDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const title = intl.formatMessage({
    id: 'pages.mysqlMonitor.title',
    defaultMessage: 'MySQL Monitor',
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

  const { instant, series, instances, jobs, loading, rateInterval } =
    useMySQLDashboard({
      variables,
      timeRange,
      clusterId: 1,
      refreshKey,
    });

  const m09Limit = maxSeriesValue(series.M09, 'Max Connections');
  const m14VisibleSeries = useMemo(
    () => hideZeroSeries(series.M14),
    [series.M14],
  );

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
          {' · '}
          rate={rateInterval}
        </>
      }
      loading={loading}
    >
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={4}>
          <StatPanel
            title="Uptime"
            value={instant.uptime}
            color={colorByThreshold(instant.uptime, [300, 3600], {
              reverse: true,
            })}
            formatter={formatDuration}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Current QPS"
            value={instant.currentQps}
            color={CHART_COLORS.primary}
            precision={1}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Connections Used %"
            value={instant.connectionsUsedPercent}
            color={colorByThreshold(instant.connectionsUsedPercent, [80, 90])}
            suffix="%"
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="InnoDB Buffer Pool"
            value={instant.innodbBufferPool}
            color={CHART_COLORS.primary}
            formatter={formatBytes}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Slow Queries /s"
            value={instant.slowQueriesPerSecond}
            color={colorByThreshold(instant.slowQueriesPerSecond, [0.01, 1])}
            precision={2}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title="Aborted Connections /s"
            value={instant.abortedConnectionsPerSecond}
            color={
              instant.abortedConnectionsPerSecond === 0
                ? CHART_COLORS.success
                : CHART_COLORS.warning
            }
            precision={2}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="MySQL Questions"
            data={series.M07}
            yFormatter={qpsFormatter}
            colorMap={{ 'mysql-1:9104': CHART_COLORS.primary }}
          />
        </PanelCol>
        <PanelCol span={12}>
          <AreaPanel
            title="MySQL Network Traffic"
            data={series.M08}
            stack
            yFormatter={bytesPerSecondFormatter}
            colorMap={networkColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="MySQL Connections"
            data={series.M09}
            yFormatter={integerFormatter}
            colorMap={connectionColors}
            thresholdLines={
              m09Limit > 0
                ? [
                    {
                      value: m09Limit,
                      label: 'Max Connections',
                      color: CHART_COLORS.error,
                    },
                  ]
                : undefined
            }
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="MySQL Client Thread Activity"
            data={series.M10}
            yFormatter={integerFormatter}
            colorMap={threadColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="MySQL Slow Queries"
            data={series.M11}
            yFormatter={perSecondFormatter}
            colorMap={{ 'Slow Queries': CHART_COLORS.warning }}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="MySQL Aborted Connections"
            data={series.M12}
            yFormatter={perSecondFormatter}
            colorMap={abortedColors}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title="MySQL Table Locks"
            data={series.M13}
            yFormatter={perSecondFormatter}
            colorMap={tableLockColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <AreaPanel
            title="MySQL Internal Memory Overview"
            data={m14VisibleSeries}
            stack
            yFormatter={formatBytes}
            colorMap={memoryColors}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="MySQL Temporary Objects"
            data={series.M15}
            yFormatter={perSecondFormatter}
            colorMap={tempObjectColors}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="MySQL Handlers"
            data={series.M16}
            yFormatter={perSecondFormatter}
            colorMap={handlerColors}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title="Top Command Counters"
            data={series.M17}
            yFormatter={perSecondFormatter}
            colorMap={commandColors}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default MySQLDashboard;
