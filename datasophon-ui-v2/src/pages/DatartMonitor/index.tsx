import { useIntl } from '@umijs/max';
import { Col, Row, Spin, Typography } from 'antd';
import { type FC, useCallback, useEffect, useMemo, useState } from 'react';
import AreaPanel from '../PrometheusMonitor/panels/AreaPanel';
import StatPanel from '../PrometheusMonitor/panels/StatPanel';
import TimeSeriesPanel from '../PrometheusMonitor/panels/TimeSeriesPanel';
import type {
  RefreshInterval,
  TimeRange,
} from '../PrometheusMonitor/toolbar/DashboardToolbar';
import {
  CHART_COLORS,
  colorByThreshold,
  formatBytes,
} from '../PrometheusMonitor/utils/formatters';
import { selectionsToRegex } from '../PrometheusMonitor/utils/promql';
import { useDatartDashboard } from './hooks/useDatartDashboard';
import {
  MOCK_APPLICATIONS,
  MOCK_HEAP_POOLS,
  MOCK_HIKARICP_POOLS,
} from './mock/datartMockData';
import DatartDashboardToolbar from './toolbar/DatartDashboardToolbar';

const { Title } = Typography;
const ROW_GUTTER: [number, number] = [16, 16];

const cpuColors = {
  'System CPU': CHART_COLORS.warning,
  'Process CPU': CHART_COLORS.primary,
  'Load 1m': '#8c8c8c',
};

const heapPoolColors = {
  Used: CHART_COLORS.primary,
  Committed: CHART_COLORS.warning,
  Max: '#8c8c8c',
};

const threadColors = {
  Daemon: '#8c8c8c',
  Live: CHART_COLORS.primary,
  Peak: CHART_COLORS.warning,
};

const hikaricpConnectionColors = {
  Active: CHART_COLORS.primary,
  Idle: CHART_COLORS.success,
  Pending: CHART_COLORS.error,
};

const hikaricpLatencyColors = {
  'Acquire Time': CHART_COLORS.warning,
  'Usage Time': CHART_COLORS.primary,
};

const tomcatColors = {
  'Current Threads': CHART_COLORS.primary,
  'Busy Threads': CHART_COLORS.warning,
  'Active Sessions': CHART_COLORS.success,
};

const tomcatByteColors = {
  Sent: CHART_COLORS.primary,
  Received: CHART_COLORS.success,
};

const logLevelColors: Record<string, string> = {
  error: CHART_COLORS.error,
  warn: CHART_COLORS.warning,
  info: CHART_COLORS.primary,
  debug: '#8c8c8c',
  trace: '#d9d9d9',
};

const integerFormatter = (value: number) => value.toFixed(0);
const perSecondFormatter = (value: number) => `${value.toFixed(2)}/s`;
const secondsFormatter = (value: number) => `${value.toFixed(3)}s`;
const millisecondFormatter = (value: number) =>
  `${(value * 1000).toFixed(1)}ms`;
const bytesPerSecondFormatter = (value: number) => `${formatBytes(value, 1)}/s`;

function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

const DatartDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedApplication, setSelectedApplication] = useState(
    MOCK_APPLICATIONS[0],
  );
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedHeapPool, setSelectedHeapPool] = useState(MOCK_HEAP_POOLS[0]);
  const [selectedHikaricpPool, setSelectedHikaricpPool] = useState(
    MOCK_HIKARICP_POOLS[0],
  );
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const title = intl.formatMessage({
    id: 'pages.datartMonitor.title',
    defaultMessage: 'Datart Monitor',
  });

  const variables = useMemo(
    () => ({
      application: selectedApplication,
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
      memory_pool_heap: selectedHeapPool,
      hikaricp: selectedHikaricpPool,
    }),
    [
      selectedApplication,
      selectedInstances,
      selectedHeapPool,
      selectedHikaricpPool,
    ],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const handleApplicationChange = useCallback((value: string) => {
    setSelectedApplication(value);
    setSelectedInstances([]);
  }, []);

  const {
    instant,
    series,
    applications,
    instances,
    heapPools,
    hikaricpPools,
    loading,
  } = useDatartDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  useEffect(() => {
    if (!applications.includes(selectedApplication) && applications[0]) {
      setSelectedApplication(applications[0]);
    }
  }, [applications, selectedApplication]);

  useEffect(() => {
    if (!heapPools.includes(selectedHeapPool) && heapPools[0]) {
      setSelectedHeapPool(heapPools[0]);
    }
  }, [heapPools, selectedHeapPool]);

  useEffect(() => {
    if (!hikaricpPools.includes(selectedHikaricpPool) && hikaricpPools[0]) {
      setSelectedHikaricpPool(hikaricpPools[0]);
    }
  }, [hikaricpPools, selectedHikaricpPool]);

  return (
    <div className="p-4" key={refreshKey}>
      <Title level={4} style={{ marginBottom: 16 }}>
        {title}
      </Title>

      <DatartDashboardToolbar
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
        refreshInterval={refreshInterval}
        onRefreshIntervalChange={setRefreshInterval}
        applications={applications}
        selectedApplication={selectedApplication}
        onApplicationChange={handleApplicationChange}
        instances={instances}
        selectedInstances={selectedInstances}
        onInstancesChange={setSelectedInstances}
        heapPools={heapPools}
        selectedHeapPool={selectedHeapPool}
        onHeapPoolChange={setSelectedHeapPool}
        hikaricpPools={hikaricpPools}
        selectedHikaricpPool={selectedHikaricpPool}
        onHikaricpPoolChange={setSelectedHikaricpPool}
        onRefresh={handleRefresh}
      />

      <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 12 }}>
        {'application="'}
        {variables.application}
        {'" instance=~"'}
        {variables.instance}
        {'" heap="'}
        {variables.memory_pool_heap}
        {'" hikaricp="'}
        {variables.hikaricp}
        {'"'}
        {' · '}
        range={timeRange}
        {loading && <Spin size="small" style={{ marginLeft: 8 }} />}
      </div>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={4}>
          <StatPanel
            title="Uptime"
            value={instant.uptime}
            color={CHART_COLORS.primary}
            formatter={formatDuration}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Heap Used %"
            value={instant.heapUsedPercent}
            color={colorByThreshold(instant.heapUsedPercent, [70, 90])}
            suffix="%"
            precision={1}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="NonHeap Used %"
            value={instant.nonHeapUsedPercent}
            color={colorByThreshold(instant.nonHeapUsedPercent, [70, 90])}
            suffix="%"
            precision={1}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="CPU Usage"
            value={instant.cpuUsage}
            color={CHART_COLORS.primary}
            suffix="%"
            precision={1}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="HikariCP Active"
            value={instant.hikaricpActive}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Error Logs /s"
            value={instant.errorLogsPerSecond}
            color={colorByThreshold(instant.errorLogsPerSecond, [0.0001, 1])}
            precision={2}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="Request Count"
            data={series.D07}
            yFormatter={perSecondFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="Response Time"
            data={series.D08}
            yFormatter={millisecondFormatter}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="CPU / Load Average"
            data={series.D09}
            yFormatter={(value) => value.toFixed(2)}
            colorMap={cpuColors}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title={`${selectedHeapPool} (heap)`}
            data={series.D10}
            yFormatter={formatBytes}
            colorMap={heapPoolColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title="GC Count"
            data={series.D11}
            yFormatter={perSecondFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="GC Stop the World Duration"
            data={series.D12}
            yFormatter={secondsFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="JVM Threads"
            data={series.D13}
            yFormatter={integerFormatter}
            colorMap={threadColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="HikariCP Connections"
            data={series.D14}
            yFormatter={integerFormatter}
            colorMap={hikaricpConnectionColors}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="HikariCP Acquire / Usage Time"
            data={series.D15}
            yFormatter={millisecondFormatter}
            colorMap={hikaricpLatencyColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER}>
        <Col span={8}>
          <TimeSeriesPanel
            title="Tomcat Threads & Sessions"
            data={series.D16}
            yFormatter={integerFormatter}
            colorMap={tomcatColors}
          />
        </Col>
        <Col span={8}>
          <AreaPanel
            title="Tomcat Sent & Received Bytes"
            data={series.D17}
            yFormatter={bytesPerSecondFormatter}
            colorMap={tomcatByteColors}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="Log Events by Level"
            data={series.D18}
            yFormatter={perSecondFormatter}
            colorMap={logLevelColors}
          />
        </Col>
      </Row>
    </div>
  );
};

export default DatartDashboard;
