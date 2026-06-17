import { Col, Row, Spin, Typography } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import type { TimeSeriesPoint } from '../PrometheusMonitor/mock/prometheusMockData';
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
import { useKyuubiDashboard } from './hooks/useKyuubiDashboard';
import KyuubiDashboardToolbar from './toolbar/KyuubiDashboardToolbar';

const { Title } = Typography;
const ROW_GUTTER: [number, number] = [16, 16];

const opStateColors = {
  Pending: CHART_COLORS.warning,
  Running: CHART_COLORS.primary,
};

const engineColors = {
  Launching: CHART_COLORS.primary,
  'Startup Permit Limit': '#8c8c8c',
};

const errorColors = {
  'Operation Error': CHART_COLORS.error,
  'Operation Failed': '#ff7a45',
  'Engine Open Failed': '#cf1322',
};

const jvmMemoryColors = {
  Used: CHART_COLORS.primary,
  'Usage Ratio': CHART_COLORS.warning,
};

const jvmPoolColors: Record<string, string> = {
  Eden: '#69b1ff',
  Survivor: '#95de64',
  'Old Gen': '#1677ff',
  Metaspace: '#faad14',
  'Code Cache': '#722ed1',
};

const integerFormatter = (value: number) => value.toFixed(0);
const millisecondFormatter = (value: number) => `${value.toFixed(0)}ms`;

function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${Math.floor(seconds)}s`;
}

function memoryUsageFormatter(value: number): string {
  if (value <= 1) return `${(value * 100).toFixed(1)}%`;
  return formatBytes(value, 1);
}

function maxSeriesValue(data: TimeSeriesPoint[], series: string): number {
  const values = data
    .filter((point) => point.series === series)
    .map((point) => point.value);
  return values.length > 0 ? Math.max(...values) : 0;
}

const KyuubiDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedConnType, setSelectedConnType] = useState(
    'connection_total_INTERACTIVE',
  );
  const [selectedOpType, setSelectedOpType] = useState('ExecuteStatement');
  const [refreshKey, setRefreshKey] = useState(0);

  const variables = useMemo(
    () => ({
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
      baseFilter: '',
      connType: selectedConnType,
      opType: selectedOpType,
      trendInterval: '5m',
    }),
    [selectedInstances, selectedConnType, selectedOpType],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const {
    instant,
    series,
    instances,
    connTypes,
    opTypes,
    trendInterval,
    loading,
  } = useKyuubiDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  const permitLimit = maxSeriesValue(series.KY10, 'Startup Permit Limit');

  return (
    <div className="p-4" key={refreshKey}>
      <Title level={4} style={{ marginBottom: 16 }}>
        Kyuubi Monitor
      </Title>

      <KyuubiDashboardToolbar
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
        refreshInterval={refreshInterval}
        onRefreshIntervalChange={setRefreshInterval}
        instances={instances}
        selectedInstances={selectedInstances}
        onInstancesChange={setSelectedInstances}
        connTypes={connTypes}
        selectedConnType={selectedConnType}
        onConnTypeChange={setSelectedConnType}
        opTypes={opTypes}
        selectedOpType={selectedOpType}
        onOpTypeChange={setSelectedOpType}
        onRefresh={handleRefresh}
      />

      <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 12 }}>
        {'instance=~"'}
        {variables.instance}
        {'"'}
        {' · '}
        conn={selectedConnType.replace('connection_total_', '')}
        {' · '}
        op={selectedOpType}
        {' · '}
        range={timeRange}
        {' · '}
        trend={trendInterval}
        {' · '}
        JVM pools use PS metric names
        {loading && <Spin size="small" style={{ marginLeft: 8 }} />}
      </div>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={4}>
          <StatPanel
            title="Instances"
            value={instant.instances}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Uptime"
            value={instant.uptime}
            color={colorByThreshold(instant.uptime, [300, 3600], {
              reverse: true,
            })}
            formatter={formatDuration}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Connection Opened"
            value={instant.connectionOpened}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Engine Total"
            value={instant.engineTotal}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Exec Pool Threads"
            value={instant.execPoolThreads}
            color={colorByThreshold(instant.execPoolThreads, [128, 256])}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Operation Error Rate"
            value={instant.operationErrorRate}
            color={
              instant.operationErrorRate > 0
                ? CHART_COLORS.error
                : CHART_COLORS.success
            }
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title={`Session (new) [${trendInterval}]`}
            data={series.KY07}
            yFormatter={integerFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title={`Operation (new) [${trendInterval}]`}
            data={series.KY08}
            yFormatter={integerFormatter}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="Operation Pending / Running"
            data={series.KY09}
            yFormatter={integerFormatter}
            colorMap={opStateColors}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="Engine Launching & Startup Permit"
            data={series.KY10}
            yFormatter={integerFormatter}
            colorMap={engineColors}
            thresholdLines={
              permitLimit > 0
                ? [
                    {
                      value: permitLimit,
                      label: 'Startup Permit Limit',
                      color: '#8c8c8c',
                    },
                  ]
                : undefined
            }
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="Connection Failed"
            data={series.KY11}
            yFormatter={integerFormatter}
            colorMap={{
              'kyuubi-1:10019': CHART_COLORS.error,
              'kyuubi-2:10019': '#ff7a45',
            }}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="Operation Error"
            data={series.KY12}
            yFormatter={integerFormatter}
            colorMap={errorColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title={`Fetch Rows [${trendInterval}]`}
            data={series.KY13}
            yFormatter={integerFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="Max Batch Pending Elapse"
            data={series.KY14}
            yFormatter={millisecondFormatter}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER}>
        <Col span={12}>
          <TimeSeriesPanel
            title="JVM Memory Usage"
            data={series.KY15}
            yFormatter={memoryUsageFormatter}
            colorMap={jvmMemoryColors}
          />
        </Col>
        <Col span={12}>
          <AreaPanel
            title="JVM Memory Pools"
            data={series.KY16}
            stack
            yFormatter={(value) => formatBytes(value, 1)}
            colorMap={jvmPoolColors}
          />
        </Col>
      </Row>
    </div>
  );
};

export default KyuubiDashboard;
