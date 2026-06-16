import { useIntl } from '@umijs/max';
import { Col, Row, Spin, Typography } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
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
  formatCompact,
} from '../PrometheusMonitor/utils/formatters';
import { selectionsToRegex } from '../PrometheusMonitor/utils/promql';
import { useDSMonitorDashboard } from './hooks/useDSMonitorDashboard';
import DSDashboardToolbar, {
  type DSApplication,
} from './toolbar/DSDashboardToolbar';

const { Text, Title } = Typography;
const ROW_GUTTER: [number, number] = [16, 16];

const dsStateColors = {
  submit: CHART_COLORS.primary,
  success: CHART_COLORS.success,
  fail: CHART_COLORS.error,
  timeout: CHART_COLORS.warning,
  retry: '#fa8c16',
  dispatch: CHART_COLORS.primary,
  failure: '#ff7a45',
  error: CHART_COLORS.error,
};

const statusColors = {
  total: CHART_COLORS.primary,
  success: CHART_COLORS.success,
  fail: CHART_COLORS.error,
  failure: CHART_COLORS.error,
};

const memoryColors = {
  used: CHART_COLORS.primary,
  committed: CHART_COLORS.warning,
  max: '#8c8c8c',
};

const cpuColors = {
  system: '#8c8c8c',
  process: CHART_COLORS.primary,
};

const logColors = {
  ERROR: CHART_COLORS.error,
  WARN: CHART_COLORS.warning,
  INFO: CHART_COLORS.primary,
  DEBUG: '#8c8c8c',
};

const integerFormatter = (value: number) => value.toFixed(0);
const percentUnitFormatter = (value: number) => `${(value * 100).toFixed(1)}%`;
const secondsFormatter = (value: number) => `${value.toFixed(3)}s`;
const millisecondsFormatter = (value: number) => `${value.toFixed(1)}ms`;
const opsFormatter = (value: number) => `${value.toFixed(2)}/s`;

function formatDuration(seconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(seconds));
  const days = Math.floor(safeSeconds / 86400);
  const hours = Math.floor((safeSeconds % 86400) / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${safeSeconds}s`;
}

const SectionHeader: FC<{ title: string; subtitle: string }> = ({
  title,
  subtitle,
}) => (
  <div
    style={{
      borderLeft: `4px solid ${CHART_COLORS.primary}`,
      margin: '24px 0 12px',
      padding: '4px 12px',
    }}
  >
    <Text strong>{title}</Text>
    <Text type="secondary" style={{ marginLeft: 8 }}>
      {subtitle}
    </Text>
  </div>
);

const DolphinSchedulerDashboard: FC = () => {
  const [application, setApplication] =
    useState<DSApplication>('master-server');
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });
  const panelTitle = (id: string) =>
    t(`pages.dolphinSchedulerMonitor.panel.${id}`);

  const variables = useMemo(
    () => ({
      application,
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
    }),
    [application, selectedInstances],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, instances, loading } = useDSMonitorDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  return (
    <div className="p-4" key={refreshKey}>
      <Title level={4} style={{ marginBottom: 16 }}>
        {t('pages.dolphinSchedulerMonitor.title')}
      </Title>

      <DSDashboardToolbar
        application={application}
        onApplicationChange={(value) => {
          setApplication(value);
          setSelectedInstances([]);
        }}
        instances={instances}
        selectedInstances={selectedInstances}
        onInstancesChange={setSelectedInstances}
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
        refreshInterval={refreshInterval}
        onRefreshIntervalChange={setRefreshInterval}
        onRefresh={handleRefresh}
      />

      <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 12 }}>
        {t('pages.dolphinSchedulerMonitor.toolbar.notice')}
        {' · '}
        application=&quot;{variables.application}&quot; instance=~&quot;{variables.instance}&quot;
        {' · '}
        range={timeRange}
        {loading && <Spin size="small" style={{ marginLeft: 8 }} />}
      </div>

      <SectionHeader
        title={t('pages.dolphinSchedulerMonitor.section.worker')}
        subtitle={t('pages.dolphinSchedulerMonitor.section.worker.subtitle')}
      />
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title={panelTitle('D-A01')}
            data={series['D-A01']}
            yFormatter={percentUnitFormatter}
            thresholdLines={[{ value: 0.8, label: '80%', color: CHART_COLORS.error }]}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-A02')} data={series['D-A02']} yFormatter={integerFormatter} />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-A03')} data={series['D-A03']} yFormatter={integerFormatter} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-A04')} data={series['D-A04']} yFormatter={integerFormatter} />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-A05')} data={series['D-A05']} yFormatter={integerFormatter} colorMap={statusColors} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={24}>
          <TimeSeriesPanel title={panelTitle('D-A06')} data={series['D-A06']} yFormatter={secondsFormatter} />
        </Col>
      </Row>

      <SectionHeader
        title={t('pages.dolphinSchedulerMonitor.section.master')}
        subtitle={t('pages.dolphinSchedulerMonitor.section.master.subtitle')}
      />
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <StatPanel title={panelTitle('D-B01')} value={instant.taskTotal} formatter={formatCompact} />
        </Col>
        <Col span={6}>
          <StatPanel title={panelTitle('D-B02')} value={instant.taskSuccessRate} suffix="%" precision={1} color={colorByThreshold(instant.taskSuccessRate, [80, 95], { reverse: true })} />
        </Col>
        <Col span={6}>
          <StatPanel title={panelTitle('D-B03')} value={instant.quartzJobTotal} formatter={formatCompact} />
        </Col>
        <Col span={6}>
          <StatPanel title={panelTitle('D-B04')} value={instant.quartzJobSuccessRate} suffix="%" precision={1} color={colorByThreshold(instant.quartzJobSuccessRate, [80, 95], { reverse: true })} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B05')} data={series['D-B05']} yFormatter={integerFormatter} />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B06')} data={series['D-B06']} yFormatter={integerFormatter} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B07')} data={series['D-B07']} yFormatter={integerFormatter} colorMap={statusColors} />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B08')} data={series['D-B08']} yFormatter={secondsFormatter} colorMap={{ avg: CHART_COLORS.primary, max: CHART_COLORS.warning }} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B09')} data={series['D-B09']} yFormatter={integerFormatter} colorMap={statusColors} />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B10')} data={series['D-B10']} yFormatter={millisecondsFormatter} colorMap={{ avg: CHART_COLORS.primary, max: CHART_COLORS.warning }} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={24}>
          <TimeSeriesPanel title={panelTitle('D-B11')} data={series['D-B11']} yFormatter={integerFormatter} colorMap={dsStateColors} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B12')} data={series['D-B12']} yFormatter={integerFormatter} colorMap={dsStateColors} />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-B13')} data={series['D-B13']} yFormatter={integerFormatter} colorMap={dsStateColors} />
        </Col>
      </Row>

      <SectionHeader
        title={t('pages.dolphinSchedulerMonitor.section.spring')}
        subtitle={t('pages.dolphinSchedulerMonitor.section.spring.subtitle')}
      />
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <StatPanel title={panelTitle('D-C01')} value={instant.uptime} formatter={formatDuration} />
        </Col>
        <Col span={8}>
          <StatPanel title={panelTitle('D-C02')} value={instant.heapUsedPercent} suffix="%" precision={1} color={colorByThreshold(instant.heapUsedPercent, [70, 90])} />
        </Col>
        <Col span={8}>
          <StatPanel title={panelTitle('D-C03')} value={instant.nonHeapUsedPercent} suffix="%" precision={1} color={colorByThreshold(instant.nonHeapUsedPercent, [70, 90])} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-C04')} data={series['D-C04']} yFormatter={opsFormatter} />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-C05')} data={series['D-C05']} yFormatter={opsFormatter} colorMap={{ series: CHART_COLORS.error }} />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-C06')} data={series['D-C06']} yFormatter={secondsFormatter} colorMap={{ avg: CHART_COLORS.primary, max: CHART_COLORS.warning }} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <AreaPanel title={panelTitle('D-C07')} data={series['D-C07']} yFormatter={formatBytes} colorMap={memoryColors} />
        </Col>
        <Col span={12}>
          <AreaPanel title={panelTitle('D-C08')} data={series['D-C08']} yFormatter={formatBytes} colorMap={memoryColors} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-C09')} data={series['D-C09']} yFormatter={percentUnitFormatter} colorMap={cpuColors} />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-C10')} data={series['D-C10']} yFormatter={integerFormatter} colorMap={{ load_1m: CHART_COLORS.primary, cpu_cores: '#8c8c8c' }} />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel title={panelTitle('D-C11')} data={series['D-C11']} yFormatter={integerFormatter} />
        </Col>
      </Row>
      <Row gutter={ROW_GUTTER}>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-C12')} data={series['D-C12']} yFormatter={integerFormatter} colorMap={logColors} />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel title={panelTitle('D-C13')} data={series['D-C13']} yFormatter={opsFormatter} />
        </Col>
      </Row>
    </div>
  );
};

export default DolphinSchedulerDashboard;
