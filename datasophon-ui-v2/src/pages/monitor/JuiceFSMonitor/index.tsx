import { useIntl } from '@umijs/max';
import { Col, Row, Spin, Typography } from 'antd';
import { type FC, useCallback, useEffect, useMemo, useState } from 'react';
import {
  CHART_COLORS,
  colorByThreshold,
  formatBytes,
  formatCompact,
} from '../_shared/charts/formatters';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import { useJuiceFSDashboard } from './hooks/useJuiceFSDashboard';
import { MOCK_VOLUMES } from './mock/juicefsMockData';
import JuiceFSDashboardToolbar from './toolbar/JuiceFSDashboardToolbar';

const { Title } = Typography;
const ROW_GUTTER: [number, number] = [16, 16];

const trafficColors = {
  Write: CHART_COLORS.primary,
  Read: CHART_COLORS.success,
};

const errorColors = {
  'Object Request Errors': CHART_COLORS.error,
  'Transaction Restarts': CHART_COLORS.warning,
};

const cacheRatioColors = {
  'By Count': CHART_COLORS.success,
  'By Bytes': CHART_COLORS.primary,
};

const objectThroughputColors = {
  PUT: CHART_COLORS.primary,
  GET: CHART_COLORS.success,
};

const resourceColors = {
  'CPU %': CHART_COLORS.warning,
  Memory: CHART_COLORS.primary,
};

function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${Math.floor(seconds)}s`;
}

const percentFormatter = (value: number) => `${value.toFixed(1)}%`;
const integerFormatter = (value: number) => formatCompact(value);
const rateFormatter = (value: number) => `${value.toFixed(1)}/s`;
const bytesPerSecondFormatter = (value: number) => `${formatBytes(value)}/s`;
const usAxisFormatter = (value: number) =>
  value >= 1000 ? `${(value / 1000).toFixed(1)}ms` : `${value.toFixed(0)}us`;
const resourceFormatter = (value: number) =>
  value > 1000 ? formatBytes(value) : percentFormatter(value);

const JuiceFSMonitor: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedVolume, setSelectedVolume] = useState(MOCK_VOLUMES[0]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const title = intl.formatMessage({
    id: 'pages.juicefsMonitor.title',
    defaultMessage: 'JuiceFS Monitor',
  });

  const variables = useMemo(
    () => ({
      name: selectedVolume,
    }),
    [selectedVolume],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, volumes, loading, rateInterval } =
    useJuiceFSDashboard({
      variables,
      timeRange,
      clusterId: 1,
      refreshKey,
    });

  useEffect(() => {
    if (volumes.length > 0 && !volumes.includes(selectedVolume)) {
      setSelectedVolume(volumes[0]);
    }
  }, [volumes, selectedVolume]);

  return (
    <div className="p-4" key={refreshKey}>
      <Title level={4} style={{ marginBottom: 16 }}>
        {title}
      </Title>

      <JuiceFSDashboardToolbar
        volume={selectedVolume}
        volumes={volumes}
        onVolumeChange={setSelectedVolume}
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
        refreshInterval={refreshInterval}
        onRefreshIntervalChange={setRefreshInterval}
        onRefresh={handleRefresh}
      />

      <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 12 }}>
        vol_name=&quot;{selectedVolume}&quot;
        {' · '}
        range={timeRange}
        {' · '}
        rate_interval={rateInterval}
        {loading && <Spin size="small" style={{ marginLeft: 8 }} />}
      </div>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={4}>
          <StatPanel
            title="Uptime"
            value={instant.uptime}
            formatter={formatDuration}
            color={
              instant.uptime < 300 ? CHART_COLORS.warning : CHART_COLORS.success
            }
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Data Size"
            value={instant.dataSize}
            formatter={formatBytes}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Files"
            value={instant.files}
            formatter={integerFormatter}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Client Sessions"
            value={instant.clientSessions}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Block Cache Hit %"
            value={instant.cacheHitPercent}
            formatter={percentFormatter}
            color={colorByThreshold(instant.cacheHitPercent, [70, 90], {
              reverse: true,
            })}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title="Staging Blocks"
            value={instant.stagingBlocks}
            color={
              instant.stagingBlocks === 0
                ? CHART_COLORS.success
                : CHART_COLORS.warning
            }
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="Operations"
            data={series.J07}
            yFormatter={rateFormatter}
          />
        </Col>
        <Col span={12}>
          <AreaPanel
            title="IO Throughput"
            data={series.J08}
            yFormatter={bytesPerSecondFormatter}
            colorMap={trafficColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title="IO Latency"
            data={series.J09}
            yFormatter={usAxisFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="Transaction Latency"
            data={series.J10}
            yFormatter={usAxisFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="Objects Latency"
            data={series.J11}
            yFormatter={usAxisFormatter}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="Objects Requests"
            data={series.J12}
            yFormatter={rateFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="Object Errors & Transaction Restarts"
            data={series.J13}
            yFormatter={rateFormatter}
            colorMap={errorColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title="Block Cache Size"
            data={series.J14}
            yFormatter={formatBytes}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="Block Cache Hit Ratio"
            data={series.J15}
            yFormatter={percentFormatter}
            colorMap={cacheRatioColors}
            thresholdLines={[
              { value: 70, label: '70%', color: CHART_COLORS.warning },
            ]}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="Objects Throughput"
            data={series.J16}
            yFormatter={bytesPerSecondFormatter}
            colorMap={objectThroughputColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER}>
        <Col span={24}>
          <TimeSeriesPanel
            title="Client CPU & Memory"
            data={series.J17}
            yFormatter={resourceFormatter}
            colorMap={resourceColors}
          />
        </Col>
      </Row>
    </div>
  );
};

export default JuiceFSMonitor;
