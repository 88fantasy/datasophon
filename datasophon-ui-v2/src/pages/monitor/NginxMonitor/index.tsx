import { useIntl } from '@umijs/max';
import { Row } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import { CHART_COLORS, colorByThreshold } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import PanelCol from '../_shared/PanelCol';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import { useNginxDashboard } from './hooks/useNginxDashboard';
import NginxDashboardToolbar from './toolbar/NginxDashboardToolbar';

// N06 Active Connections — 4 系列固定配色
const nginxConnColors = {
  Active: CHART_COLORS.primary,
  Reading: '#13c2c2',
  Writing: CHART_COLORS.warning,
  Waiting: '#8c8c8c',
};

// N05 Processed Connections — accepted/handled 重合时无丢弃
const nginxProcColors = {
  Accepted: CHART_COLORS.primary,
  Handled: CHART_COLORS.success,
};

const reqFormatter = (value: number) => `${value.toFixed(1)}/s`;
const connFormatter = (value: number) => value.toFixed(0);

const NginxDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string, defaultMessage?: string) =>
    intl.formatMessage({ id, defaultMessage });

  const variables = useMemo(
    () => ({
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
    }),
    [selectedInstances],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, instances, loading } = useNginxDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={t('pages.nginxMonitor.title', 'Nginx Monitor')}
      toolbar={
        <NginxDashboardToolbar
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          instances={instances}
          selectedInstances={selectedInstances}
          onInstancesChange={setSelectedInstances}
          onRefresh={handleRefresh}
        />
      }
      meta={
        <>
          instance=~&quot;{variables.instance}&quot;
          {' · '}
          range={timeRange}
          {' · '}
          {/* stub_status 只覆盖 Traffic + Saturation，Latency/Errors 需 nginxlog-exporter 补强 */}
          Traffic + Saturation only (stub_status)
        </>
      }
      loading={loading}
    >
      {/* R1 — Status Stat */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <StatPanel
            title={t('pages.nginxMonitor.panel.status', 'NGINX Status')}
            value={instant.status}
            color={
              instant.status === 1 ? CHART_COLORS.success : CHART_COLORS.error
            }
            formatter={(v) => (v === 1 ? 'Up' : 'Down')}
          />
        </PanelCol>
        <PanelCol span={8}>
          <StatPanel
            title={t(
              'pages.nginxMonitor.panel.activeConnections',
              'Active Connections',
            )}
            value={instant.activeConnections}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={8}>
          <StatPanel
            title={t(
              'pages.nginxMonitor.panel.droppedConnections',
              'Dropped Connections /s',
            )}
            value={instant.droppedConnections}
            color={colorByThreshold(instant.droppedConnections, [0.001, 1])}
            precision={2}
          />
        </PanelCol>
      </Row>

      {/* R2 — Traffic */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.nginxMonitor.panel.totalRequests',
              'Total Requests',
            )}
            data={series.N04}
            yFormatter={reqFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.nginxMonitor.panel.processedConnections',
              'Processed Connections',
            )}
            data={series.N05}
            yFormatter={connFormatter}
            colorMap={nginxProcColors}
          />
        </PanelCol>
      </Row>

      {/* R3 — Saturation */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={24}>
          <AreaPanel
            title={t(
              'pages.nginxMonitor.panel.activeConnectionsBreakdown',
              'Active Connections',
            )}
            data={series.N06}
            stack
            yFormatter={connFormatter}
            colorMap={nginxConnColors}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default NginxDashboard;
