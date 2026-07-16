import { useIntl } from '@umijs/max';
import { Row, Tabs, Typography } from 'antd';
import { type FC, useCallback, useEffect, useMemo, useState } from 'react';
import { CHART_COLORS, formatBytes } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import useStyles from '../_shared/monitorStyles';
import PanelCol from '../_shared/PanelCol';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import { useDorisMonitorDashboard } from './hooks/useDorisMonitorDashboard';
import type { DorisDashboardSegment } from './panelQueries';
import DorisDashboardToolbar, {
  type DorisRateInterval,
} from './toolbar/DorisDashboardToolbar';

const { Text } = Typography;

const dorisRoleColors = {
  fe: CHART_COLORS.primary,
  be: CHART_COLORS.success,
  error: CHART_COLORS.error,
  warning: CHART_COLORS.warning,
  saturation: '#fa8c16',
  reference: '#8c8c8c',
};

const latencyColors = {
  p50: CHART_COLORS.success,
  p75: CHART_COLORS.warning,
  p99: CHART_COLORS.error,
};

const compactionColors = {
  base: CHART_COLORS.primary,
  cumulative: CHART_COLORS.success,
};

const errorColors = {
  cumulative: '#8c8c8c',
  rate_1m: CHART_COLORS.error,
};

const networkColors = {
  send: CHART_COLORS.primary,
  recv: CHART_COLORS.success,
};

const integerFormatter = (value: number) => value.toFixed(0);
const percentFormatter = (value: number) => `${value.toFixed(1)}%`;
const percentPreciseFormatter = (value: number) => `${value.toFixed(2)}%`;
const percentUnitFormatter = (value: number) => `${(value * 100).toFixed(1)}%`;
const opsFormatter = (value: number) => `${value.toFixed(2)}/s`;
const reqFormatter = (value: number) => `${value.toFixed(2)} req/s`;
const queryFormatter = (value: number) => `${value.toFixed(2)} query/s`;
const millisecondFormatter = (value: number) => `${value.toFixed(1)}ms`;
const millisecondPreciseFormatter = (value: number) => `${value.toFixed(2)}ms`;
const bytesPerSecondFormatter = (value: number) => `${formatBytes(value)}/s`;
const rowsPerSecondFormatter = (value: number) => `${value.toFixed(0)} rows/s`;

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

interface DorisDashboardProps {
  clusterId?: number;
  embedded?: boolean;
}

const DorisDashboard: FC<DorisDashboardProps> = ({
  clusterId = 1,
  embedded = false,
}) => {
  const [selectedCluster, setSelectedCluster] = useState('');
  const [selectedFeInstances, setSelectedFeInstances] = useState<string[]>([]);
  const [selectedBeInstances, setSelectedBeInstances] = useState<string[]>([]);
  const [rateInterval, setRateInterval] = useState<DorisRateInterval>('2m');
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [refreshKey, setRefreshKey] = useState(0);
  const [activeSegment, setActiveSegment] =
    useState<DorisDashboardSegment>('cluster');

  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });
  const panelTitle = (id: string) => t(`pages.dorisMonitor.panel.${id}`);

  const variables = useMemo(
    () => ({
      cluster: selectedCluster || 'doris',
      feInstance:
        selectedFeInstances.length > 0
          ? selectionsToRegex(selectedFeInstances)
          : '.+',
      beInstance:
        selectedBeInstances.length > 0
          ? selectionsToRegex(selectedBeInstances)
          : '.+',
      interval: rateInterval,
    }),
    [selectedCluster, selectedFeInstances, selectedBeInstances, rateInterval],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, clusters, feInstances, beInstances, loading } =
    useDorisMonitorDashboard({
      variables,
      activeSegment,
      timeRange,
      clusterId,
      refreshKey,
    });

  useEffect(() => {
    if (!selectedCluster && clusters.length > 0) {
      setSelectedCluster(clusters[0]);
    }
  }, [clusters, selectedCluster]);

  const effectiveCluster = selectedCluster || clusters[0] || 'doris';

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={t('pages.dorisMonitor.title')}
      toolbar={
        <DorisDashboardToolbar
          hideClusterSelect={embedded}
          cluster={effectiveCluster}
          clusters={clusters}
          onClusterChange={(value) => {
            setSelectedCluster(value);
            setSelectedFeInstances([]);
            setSelectedBeInstances([]);
          }}
          feInstances={feInstances}
          selectedFeInstances={selectedFeInstances}
          onFeInstancesChange={setSelectedFeInstances}
          beInstances={beInstances}
          selectedBeInstances={selectedBeInstances}
          onBeInstancesChange={setSelectedBeInstances}
          rateInterval={rateInterval}
          onRateIntervalChange={setRateInterval}
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          onRefresh={handleRefresh}
        />
      }
      meta={
        <>
          {t('pages.dorisMonitor.toolbar.notice')}
          {' · '}
          {`fe_instance=~"${variables.feInstance}" be_instance=~"${variables.beInstance}" interval=${variables.interval}`}
          {' · '}
          range={timeRange}
        </>
      }
      loading={loading}
    >
      <Tabs
        activeKey={activeSegment}
        onChange={(key) => setActiveSegment(key as DorisDashboardSegment)}
        destroyOnHidden
        items={[
          {
            key: 'cluster',
            label: t('pages.dorisMonitor.section.cluster'),
            children: (
              <>
                <SectionHeader
                  title={t('pages.dorisMonitor.section.cluster')}
                  subtitle={t('pages.dorisMonitor.section.cluster.subtitle')}
                />
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={4}>
                    <StatPanel
                      title={panelTitle('DO-A01')}
                      value={instant.feNodeCount}
                      color={dorisRoleColors.fe}
                    />
                  </PanelCol>
                  <PanelCol span={4}>
                    <StatPanel
                      title={panelTitle('DO-A02')}
                      value={instant.feAliveCount}
                      color={
                        instant.feAliveCount === instant.feNodeCount
                          ? CHART_COLORS.success
                          : CHART_COLORS.error
                      }
                    />
                  </PanelCol>
                  <PanelCol span={4}>
                    <StatPanel
                      title={panelTitle('DO-A03')}
                      value={instant.beNodeCount}
                      color={dorisRoleColors.be}
                    />
                  </PanelCol>
                  <PanelCol span={4}>
                    <StatPanel
                      title={panelTitle('DO-A04')}
                      value={instant.beAliveCount}
                      color={
                        instant.beAliveCount === instant.beNodeCount
                          ? CHART_COLORS.success
                          : CHART_COLORS.error
                      }
                    />
                  </PanelCol>
                  <PanelCol span={4}>
                    <StatPanel
                      title={panelTitle('DO-A05')}
                      value={instant.usedCapacityBytes}
                      color="#5c6b77"
                      formatter={formatBytes}
                    />
                  </PanelCol>
                  <PanelCol span={4}>
                    <StatPanel
                      title={panelTitle('DO-A06')}
                      value={instant.totalCapacityBytes}
                      color={dorisRoleColors.reference}
                      formatter={formatBytes}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={8}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-A07')}
                      data={series['DO-A07']}
                      yFormatter={opsFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={8}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-A08')}
                      data={series['DO-A08']}
                      yFormatter={percentFormatter}
                      thresholdLines={[
                        {
                          value: 70,
                          label: '70%',
                          color: CHART_COLORS.warning,
                        },
                        { value: 90, label: '90%', color: CHART_COLORS.error },
                      ]}
                    />
                  </PanelCol>
                  <PanelCol span={8}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-A09')}
                      data={series['DO-A09']}
                      yFormatter={(v) => v.toFixed(0)}
                    />
                  </PanelCol>
                </Row>
              </>
            ),
          },
          {
            key: 'fe',
            label: t('pages.dorisMonitor.section.fe'),
            children: (
              <>
                <SectionHeader
                  title={t('pages.dorisMonitor.section.fe')}
                  subtitle={t('pages.dorisMonitor.section.fe.subtitle')}
                />
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B01')}
                      data={series['DO-B01']}
                      yFormatter={reqFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B02')}
                      data={series['DO-B02']}
                      yFormatter={queryFormatter}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B03')}
                      data={series['DO-B03']}
                      yFormatter={millisecondFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B04')}
                      data={series['DO-B04']}
                      yFormatter={millisecondFormatter}
                      colorMap={latencyColors}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B05')}
                      data={series['DO-B05']}
                      yFormatter={integerFormatter}
                      colorMap={errorColors}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B06')}
                      data={series['DO-B06']}
                      yFormatter={percentPreciseFormatter}
                      colorMap={{ series: CHART_COLORS.error }}
                      thresholdLines={[
                        { value: 1, label: '1%', color: CHART_COLORS.error },
                      ]}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B07')}
                      data={series['DO-B07']}
                      yFormatter={integerFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B08')}
                      data={series['DO-B08']}
                      yFormatter={integerFormatter}
                      thresholdLines={[
                        {
                          value: 100,
                          label: '100',
                          color: dorisRoleColors.saturation,
                        },
                      ]}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B09')}
                      data={series['DO-B09']}
                      yFormatter={integerFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B12')}
                      data={series['DO-B12']}
                      yFormatter={millisecondFormatter}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <AreaPanel
                      title={panelTitle('DO-B10')}
                      data={series['DO-B10']}
                      yFormatter={formatBytes}
                      colorMap={{
                        used: CHART_COLORS.primary,
                        max: dorisRoleColors.reference,
                      }}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-B11')}
                      data={series['DO-B11']}
                      yFormatter={millisecondFormatter}
                      colorMap={{
                        gc_count: dorisRoleColors.reference,
                        avg_time_ms: CHART_COLORS.error,
                      }}
                    />
                  </PanelCol>
                </Row>
              </>
            ),
          },
          {
            key: 'be',
            label: t('pages.dorisMonitor.section.be'),
            children: (
              <>
                <SectionHeader
                  title={t('pages.dorisMonitor.section.be')}
                  subtitle={t('pages.dorisMonitor.section.be.subtitle')}
                />
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={8}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C01')}
                      data={series['DO-C01']}
                      yFormatter={(v) => v.toFixed(0)}
                    />
                  </PanelCol>
                  <PanelCol span={8}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C02')}
                      data={series['DO-C02']}
                      yFormatter={formatBytes}
                    />
                  </PanelCol>
                  <PanelCol span={8}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C03')}
                      data={series['DO-C03']}
                      yFormatter={percentUnitFormatter}
                      thresholdLines={[
                        { value: 0.8, label: '80%', color: CHART_COLORS.error },
                      ]}
                      colorMap={{
                        local_used_pct: CHART_COLORS.error,
                        avail_pct: CHART_COLORS.success,
                      }}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C04')}
                      data={series['DO-C04']}
                      yFormatter={percentFormatter}
                      thresholdLines={[
                        { value: 80, label: '80%', color: CHART_COLORS.error },
                      ]}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C05')}
                      data={series['DO-C05']}
                      yFormatter={bytesPerSecondFormatter}
                      colorMap={compactionColors}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C06')}
                      data={series['DO-C06']}
                      yFormatter={bytesPerSecondFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C07')}
                      data={series['DO-C07']}
                      yFormatter={rowsPerSecondFormatter}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C08')}
                      data={series['DO-C08']}
                      yFormatter={bytesPerSecondFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C09')}
                      data={series['DO-C09']}
                      yFormatter={rowsPerSecondFormatter}
                    />
                  </PanelCol>
                </Row>
                <Row gutter={MONITOR_ROW_GUTTER}>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C10')}
                      data={series['DO-C10']}
                      yFormatter={millisecondPreciseFormatter}
                    />
                  </PanelCol>
                  <PanelCol span={12}>
                    <TimeSeriesPanel
                      title={panelTitle('DO-C11')}
                      data={series['DO-C11']}
                      yFormatter={bytesPerSecondFormatter}
                      colorMap={networkColors}
                    />
                  </PanelCol>
                </Row>
              </>
            ),
          },
        ]}
      />
    </MonitorDashboardLayout>
  );
};

export default DorisDashboard;
