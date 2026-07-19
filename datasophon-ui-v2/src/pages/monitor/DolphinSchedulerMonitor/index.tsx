import { useIntl } from '@umijs/max';
import { Row, Tabs, Typography } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import {
  CHART_COLORS,
  colorByThreshold,
  formatBytes,
  formatCompact,
} from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import useStyles from '../_shared/monitorStyles';
import PanelCol from '../_shared/PanelCol';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import type { TimeSeriesPoint } from '../_shared/types';
import { useDSMonitorDashboard } from './hooks/useDSMonitorDashboard';
import type { DSApplication } from './panelQueries';
import DSDashboardToolbar from './toolbar/DSDashboardToolbar';

const { Text } = Typography;

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
}) => {
  const { styles } = useStyles();

  return (
    <div className={styles.sectionHeader}>
      <Text strong>{title}</Text>
      <Text type="secondary" style={{ marginLeft: 8 }}>
        {subtitle}
      </Text>
    </div>
  );
};

// ─── 公共 Spring Boot / JVM 指标区块（4 个 Tab 共用）────────────────────────────

interface SpringSectionProps {
  instant: Record<string, number>;
  series: Record<string, TimeSeriesPoint[]>;
  panelTitle: (id: string) => string;
  t: (id: string) => string;
}

const SpringSection: FC<SpringSectionProps> = ({
  instant,
  series,
  panelTitle,
  t,
}) => (
  <>
    <SectionHeader
      title={t('pages.dolphinSchedulerMonitor.section.spring')}
      subtitle={t('pages.dolphinSchedulerMonitor.section.spring.subtitle')}
    />
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={8}>
        <StatPanel
          title={panelTitle('D-C01')}
          value={instant['D-C01'] ?? 0}
          formatter={formatDuration}
        />
      </PanelCol>
      <PanelCol span={8}>
        <StatPanel
          title={panelTitle('D-C02')}
          value={instant['D-C02'] ?? 0}
          suffix="%"
          precision={1}
          color={colorByThreshold(instant['D-C02'] ?? 0, [70, 90])}
        />
      </PanelCol>
      <PanelCol span={8}>
        <StatPanel
          title={panelTitle('D-C03')}
          value={instant['D-C03'] ?? 0}
          suffix="%"
          precision={1}
          color={colorByThreshold(instant['D-C03'] ?? 0, [70, 90])}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-C04')}
          data={series['D-C04']}
          yFormatter={opsFormatter}
        />
      </PanelCol>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-C05')}
          data={series['D-C05']}
          yFormatter={opsFormatter}
          colorMap={{ series: CHART_COLORS.error }}
        />
      </PanelCol>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-C06')}
          data={series['D-C06']}
          yFormatter={secondsFormatter}
          colorMap={{ avg: CHART_COLORS.primary, max: CHART_COLORS.warning }}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <AreaPanel
          title={panelTitle('D-C07')}
          data={series['D-C07']}
          yFormatter={formatBytes}
          colorMap={memoryColors}
        />
      </PanelCol>
      <PanelCol span={12}>
        <AreaPanel
          title={panelTitle('D-C08')}
          data={series['D-C08']}
          yFormatter={formatBytes}
          colorMap={memoryColors}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-C09')}
          data={series['D-C09']}
          yFormatter={percentUnitFormatter}
          colorMap={cpuColors}
        />
      </PanelCol>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-C10')}
          data={series['D-C10']}
          yFormatter={integerFormatter}
          colorMap={{ load_1m: CHART_COLORS.primary, cpu_cores: '#8c8c8c' }}
        />
      </PanelCol>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-C11')}
          data={series['D-C11']}
          yFormatter={integerFormatter}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-C12')}
          data={series['D-C12']}
          yFormatter={opsFormatter}
          colorMap={logColors}
        />
      </PanelCol>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-C13')}
          data={series['D-C13']}
          yFormatter={opsFormatter}
        />
      </PanelCol>
    </Row>
  </>
);

// ─── Master Server 专属指标区块 ────────────────────────────────────────────────

interface MasterSectionProps {
  instant: Record<string, number>;
  series: Record<string, TimeSeriesPoint[]>;
  panelTitle: (id: string) => string;
  t: (id: string) => string;
}

const MasterSection: FC<MasterSectionProps> = ({
  instant,
  series,
  panelTitle,
  t,
}) => (
  <>
    <SectionHeader
      title={t('pages.dolphinSchedulerMonitor.section.master')}
      subtitle={t('pages.dolphinSchedulerMonitor.section.master.subtitle')}
    />
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={6}>
        <StatPanel
          title={panelTitle('D-B01')}
          value={instant['D-B01'] ?? 0}
          formatter={formatCompact}
        />
      </PanelCol>
      <PanelCol span={6}>
        <StatPanel
          title={panelTitle('D-B02')}
          value={instant['D-B02'] ?? 0}
          suffix="%"
          precision={1}
          color={colorByThreshold(instant['D-B02'] ?? 0, [80, 95], {
            reverse: true,
          })}
        />
      </PanelCol>
      <PanelCol span={6}>
        <StatPanel
          title={panelTitle('D-B03')}
          value={instant['D-B03'] ?? 0}
          formatter={formatCompact}
        />
      </PanelCol>
      <PanelCol span={6}>
        <StatPanel
          title={panelTitle('D-B04')}
          value={instant['D-B04'] ?? 0}
          suffix="%"
          precision={1}
          color={colorByThreshold(instant['D-B04'] ?? 0, [80, 95], {
            reverse: true,
          })}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B05')}
          data={series['D-B05']}
          yFormatter={integerFormatter}
        />
      </PanelCol>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B06')}
          data={series['D-B06']}
          yFormatter={integerFormatter}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B07')}
          data={series['D-B07']}
          yFormatter={integerFormatter}
          colorMap={statusColors}
        />
      </PanelCol>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B08')}
          data={series['D-B08']}
          yFormatter={secondsFormatter}
          colorMap={{ avg: CHART_COLORS.primary, max: CHART_COLORS.warning }}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B09')}
          data={series['D-B09']}
          yFormatter={integerFormatter}
          colorMap={statusColors}
        />
      </PanelCol>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B10')}
          data={series['D-B10']}
          yFormatter={millisecondsFormatter}
          colorMap={{ avg: CHART_COLORS.primary, max: CHART_COLORS.warning }}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={24}>
        <TimeSeriesPanel
          title={panelTitle('D-B11')}
          data={series['D-B11']}
          yFormatter={integerFormatter}
          colorMap={dsStateColors}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B12')}
          data={series['D-B12']}
          yFormatter={integerFormatter}
          colorMap={dsStateColors}
        />
      </PanelCol>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-B13')}
          data={series['D-B13']}
          yFormatter={integerFormatter}
          colorMap={dsStateColors}
        />
      </PanelCol>
    </Row>
  </>
);

// ─── Worker Server 专属指标区块 ───────────────────────────────────────────────

interface WorkerSectionProps {
  series: Record<string, TimeSeriesPoint[]>;
  panelTitle: (id: string) => string;
  t: (id: string) => string;
}

const WorkerSection: FC<WorkerSectionProps> = ({ series, panelTitle, t }) => (
  <>
    <SectionHeader
      title={t('pages.dolphinSchedulerMonitor.section.worker')}
      subtitle={t('pages.dolphinSchedulerMonitor.section.worker.subtitle')}
    />
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-A01')}
          data={series['D-A01']}
          yFormatter={percentUnitFormatter}
          thresholdLines={[
            { value: 0.8, label: '80%', color: CHART_COLORS.error },
          ]}
        />
      </PanelCol>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-A02')}
          data={series['D-A02']}
          yFormatter={integerFormatter}
        />
      </PanelCol>
      <PanelCol span={8}>
        <TimeSeriesPanel
          title={panelTitle('D-A03')}
          data={series['D-A03']}
          yFormatter={integerFormatter}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-A04')}
          data={series['D-A04']}
          yFormatter={integerFormatter}
        />
      </PanelCol>
      <PanelCol span={12}>
        <TimeSeriesPanel
          title={panelTitle('D-A05')}
          data={series['D-A05']}
          yFormatter={integerFormatter}
          colorMap={statusColors}
        />
      </PanelCol>
    </Row>
    <Row gutter={MONITOR_ROW_GUTTER}>
      <PanelCol span={24}>
        <TimeSeriesPanel
          title={panelTitle('D-A06')}
          data={series['D-A06']}
          yFormatter={secondsFormatter}
        />
      </PanelCol>
    </Row>
  </>
);

// ─── 主页面 ──────────────────────────────────────────────────────────────────

export interface DSDashboardProps {
  clusterId: number;
}

const DolphinSchedulerDashboard: FC<DSDashboardProps> = ({ clusterId }) => {
  const [activeSegment, setActiveSegment] =
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
      application: activeSegment,
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
    }),
    [activeSegment, selectedInstances],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, instances, loading } = useDSMonitorDashboard({
    variables,
    activeSegment,
    timeRange,
    clusterId,
    refreshKey,
  });

  const sharedSectionProps = { instant, series, panelTitle, t };

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      toolbar={
        <DSDashboardToolbar
          instances={instances}
          selectedInstances={selectedInstances}
          onInstancesChange={setSelectedInstances}
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          onRefresh={handleRefresh}
        />
      }
      meta={
        <>
          {t('pages.dolphinSchedulerMonitor.toolbar.notice')}
          {' · '}
          application=&quot;{variables.application}&quot; instance=~&quot;
          {variables.instance}&quot;
          {' · '}
          range={timeRange}
        </>
      }
      loading={loading}
    >
      <Tabs
        activeKey={activeSegment}
        onChange={(key) => {
          setActiveSegment(key as DSApplication);
          setSelectedInstances([]);
        }}
        destroyOnHidden
        items={[
          {
            key: 'master-server',
            label: t('pages.dolphinSchedulerMonitor.tab.master'),
            children: (
              <>
                <MasterSection {...sharedSectionProps} />
                <SpringSection {...sharedSectionProps} />
              </>
            ),
          },
          {
            key: 'worker-server',
            label: t('pages.dolphinSchedulerMonitor.tab.worker'),
            children: (
              <>
                <WorkerSection series={series} panelTitle={panelTitle} t={t} />
                <SpringSection {...sharedSectionProps} />
              </>
            ),
          },
          {
            key: 'api-server',
            label: t('pages.dolphinSchedulerMonitor.tab.api'),
            children: <SpringSection {...sharedSectionProps} />,
          },
          {
            key: 'alert-server',
            label: t('pages.dolphinSchedulerMonitor.tab.alert'),
            children: <SpringSection {...sharedSectionProps} />,
          },
        ]}
      />
    </MonitorDashboardLayout>
  );
};

export default DolphinSchedulerDashboard;
