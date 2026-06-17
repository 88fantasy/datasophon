import { useIntl } from '@umijs/max';
import { Col, Row, Spin, Typography } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import {
  CHART_COLORS,
  colorByThreshold,
  formatBytes,
} from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import { useZKDashboard } from './hooks/useZKDashboard';
import ZKDashboardToolbar from './toolbar/ZKDashboardToolbar';

const { Title } = Typography;
const ROW_GUTTER: [number, number] = [16, 16];

const zkLatencyColors = {
  max: CHART_COLORS.error,
  avg: CHART_COLORS.primary,
  min: CHART_COLORS.success,
};

const zkSessionColors = {
  global: CHART_COLORS.primary,
  local: CHART_COLORS.success,
};

const zkZnodeColors = {
  znode_count: CHART_COLORS.primary,
  ephemerals: CHART_COLORS.warning,
};

const zkPacketColors = {
  received: CHART_COLORS.primary,
  sent: CHART_COLORS.success,
};

const zkErrorColors = {
  conn_rejected: '#ff7a45',
  conn_drop: '#fa541c',
  unrecoverable: CHART_COLORS.error,
  digest_mismatch: '#cf1322',
};

const zkLearnerColors = {
  learners: CHART_COLORS.primary,
  synced_observers: CHART_COLORS.success,
};

const zkQuorumColors = {
  commits: CHART_COLORS.primary,
  snapshots: CHART_COLORS.warning,
  proposals: CHART_COLORS.success,
};

const integerFormatter = (value: number) => value.toFixed(0);
const millisecondFormatter = (value: number) => `${value.toFixed(1)}ms`;
const wholeMillisecondFormatter = (value: number) => `${value.toFixed(0)}ms`;
const opsFormatter = (value: number) => `${value.toFixed(3)}/s`;
const pauseFormatter = (value: number) => `${value.toFixed(2)}ms/s`;

function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);

  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${seconds}s`;
}

const ZooKeeperDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const title = intl.formatMessage({
    id: 'pages.zookeeperMonitor.title',
    defaultMessage: 'ZooKeeper Monitor',
  });
  const t = (id: string) => intl.formatMessage({ id });

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

  const { instant, series, instances, jobs, loading } = useZKDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  return (
    <div className="p-4" key={refreshKey}>
      <Title level={4} style={{ marginBottom: 16 }}>
        {title}
      </Title>

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

      <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 12 }}>
        instance=~&quot;{variables.instance}&quot; job=~&quot;{variables.job}
        &quot;
        {' · '}
        range={timeRange}
        {loading && <Spin size="small" style={{ marginLeft: 8 }} />}
      </div>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={4}>
          <StatPanel
            title={t('pages.zookeeperMonitor.panel.quorumSize')}
            value={instant.quorumSize}
            color={colorByThreshold(instant.quorumSize, [3, 3], {
              reverse: true,
            })}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.zookeeperMonitor.panel.leaderUptime')}
            value={instant.leaderUptime}
            color={CHART_COLORS.primary}
            formatter={formatDuration}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.zookeeperMonitor.panel.jvmThreads')}
            value={instant.jvmThreads}
            color={colorByThreshold(instant.jvmThreads, [200, 500])}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.zookeeperMonitor.panel.deadlockedThreads')}
            value={instant.deadlockedThreads}
            color={
              instant.deadlockedThreads === 0
                ? CHART_COLORS.success
                : CHART_COLORS.error
            }
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.zookeeperMonitor.panel.aliveConnections')}
            value={instant.aliveConnections}
            color={CHART_COLORS.primary}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.zookeeperMonitor.panel.openFileDescriptors')}
            value={instant.openFileDescriptors}
            color={colorByThreshold(instant.openFileDescriptors, [5000, 8000])}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.outstandingRequests')}
            data={series.Z07}
            yFormatter={integerFormatter}
            thresholdLines={[
              { value: 10, label: '10', color: CHART_COLORS.warning },
            ]}
          />
        </Col>
        <Col span={16}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.requestLatency')}
            data={series.Z08}
            yFormatter={millisecondFormatter}
            colorMap={zkLatencyColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.sessions')}
            data={series.Z09}
            yFormatter={integerFormatter}
            colorMap={zkSessionColors}
          />
        </Col>
        <Col span={6}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.znodes')}
            data={series.Z10}
            yFormatter={integerFormatter}
            colorMap={zkZnodeColors}
          />
        </Col>
        <Col span={6}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.approximateDataSize')}
            data={series.Z11}
            yFormatter={formatBytes}
          />
        </Col>
        <Col span={6}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.watchCount')}
            data={series.Z12}
            yFormatter={integerFormatter}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.packets')}
            data={series.Z13}
            yFormatter={integerFormatter}
            colorMap={zkPacketColors}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.aliveConnectionsTrend')}
            data={series.Z14}
            yFormatter={integerFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.connectionDataErrors')}
            data={series.Z15}
            yFormatter={integerFormatter}
            colorMap={zkErrorColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.electionTime')}
            data={series.Z16}
            yFormatter={wholeMillisecondFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.learnersObservers')}
            data={series.Z17}
            yFormatter={integerFormatter}
            colorMap={zkLearnerColors}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.quorumCounts')}
            data={series.Z18}
            yFormatter={integerFormatter}
            colorMap={zkQuorumColors}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.fsyncTime')}
            data={series.Z19}
            yFormatter={millisecondFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.snapshotTime')}
            data={series.Z20}
            yFormatter={millisecondFormatter}
          />
        </Col>
      </Row>

      <Row gutter={ROW_GUTTER}>
        <Col span={8}>
          <AreaPanel
            title={t('pages.zookeeperMonitor.panel.jvmMemoryPool')}
            data={series.Z21}
            yFormatter={formatBytes}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.gcCollectionRate')}
            data={series.Z22}
            yFormatter={opsFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.zookeeperMonitor.panel.jvmGcPauseTime')}
            data={series.Z23}
            yFormatter={pauseFormatter}
          />
        </Col>
      </Row>
    </div>
  );
};

export default ZooKeeperDashboard;
