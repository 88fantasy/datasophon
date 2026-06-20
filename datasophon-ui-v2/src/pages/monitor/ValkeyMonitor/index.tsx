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
import { useValkeyDashboard } from './hooks/useValkeyDashboard';
import ValkeyDashboardToolbar from './toolbar/ValkeyDashboardToolbar';

// Seconds-based formatDuration (same pattern as DolphinSchedulerMonitor)
function formatDuration(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  const days = Math.floor(safe / 86400);
  const hours = Math.floor((safe % 86400) / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${safe}s`;
}

// V07 Avg Time — ratio result is in seconds, display as ms
const msFormatter = (value: number) => `${(value * 1000).toFixed(3)}ms`;
const intFormatter = (value: number) => value.toFixed(0);
const opsFormatter = (value: number) => `${value.toFixed(2)}/s`;
const bytesFormatter = (value: number) => formatBytes(value, 1);
const bytesPerSecFormatter = (value: number) => `${formatBytes(value, 1)}/s`;
const pctFormatter = (value: number) => `${value.toFixed(1)}%`;

const valkeyHitsColors = {
  Hits: CHART_COLORS.success,
  Misses: CHART_COLORS.warning,
};

const valkeyNetColors = {
  Input: CHART_COLORS.success,
  Output: CHART_COLORS.primary,
};

const valkeyMemColors = {
  Used: CHART_COLORS.primary,
  Max: CHART_COLORS.error,
};

const valkeyClientColors = {
  Connected: CHART_COLORS.primary,
  Blocked: CHART_COLORS.warning,
};

const valkeyKeyColors = {
  'Not-Expiring': CHART_COLORS.primary,
  Expiring: CHART_COLORS.success,
};

const valkeyKeyTtlColors = {
  Expired: CHART_COLORS.primary,
  Evicted: CHART_COLORS.error,
};

const ValkeyDashboard: FC = () => {
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

  const { instant, series, instances, loading } = useValkeyDashboard({
    variables,
    timeRange,
    clusterId: 1,
    refreshKey,
  });

  // V03 Memory %: memoryUsagePct=-1 means maxmemory not configured
  const memoryPctValue =
    instant.memoryUsagePct < 0 ? 0 : instant.memoryUsagePct;
  const memoryPctColor =
    instant.memoryUsagePct < 0
      ? CHART_COLORS.primary
      : colorByThreshold(instant.memoryUsagePct, [80, 95]);
  const memoryPctFormatter =
    instant.memoryUsagePct < 0 ? () => 'unlimited' : pctFormatter;

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={t('pages.valkeyMonitor.title', 'Valkey Monitor')}
      toolbar={
        <ValkeyDashboardToolbar
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
        </>
      }
      loading={loading}
    >
      {/* R1 — Overview Stat */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.valkeyMonitor.panel.maxUptime', 'Max Uptime')}
            value={instant.maxUptime}
            color={colorByThreshold(instant.maxUptime, [300, 300], {
              reverse: true,
            })}
            formatter={formatDuration}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.valkeyMonitor.panel.clients', 'Clients')}
            value={instant.clients}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.valkeyMonitor.panel.memoryUsage', 'Memory Usage')}
            value={memoryPctValue}
            color={memoryPctColor}
            suffix={instant.memoryUsagePct < 0 ? undefined : '%'}
            precision={instant.memoryUsagePct < 0 ? undefined : 1}
            formatter={
              instant.memoryUsagePct < 0 ? memoryPctFormatter : undefined
            }
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.valkeyMonitor.panel.cacheHitPct', 'Cache Hit %')}
            value={instant.cacheHitPct}
            color={colorByThreshold(instant.cacheHitPct, [80, 95], {
              reverse: true,
            })}
            suffix="%"
            precision={1}
          />
        </PanelCol>
      </Row>

      {/* R2 — Traffic */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.totalCommands',
              'Total Commands / sec',
            )}
            data={series.V05}
            yFormatter={opsFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.hitsAndMisses',
              'Hits / Misses per Sec',
            )}
            data={series.V06}
            yFormatter={opsFormatter}
            colorMap={valkeyHitsColors}
          />
        </PanelCol>
      </Row>

      {/* R3 — Latency & Network */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.avgTimeByCommand',
              'Avg Time by Command',
            )}
            data={series.V07}
            yFormatter={msFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <AreaPanel
            title={t('pages.valkeyMonitor.panel.networkIO', 'Network I/O')}
            data={series.V08}
            yFormatter={bytesPerSecFormatter}
            colorMap={valkeyNetColors}
          />
        </PanelCol>
      </Row>

      {/* R4 — Memory & Connections */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.memoryUsageTrend',
              'Total Memory Usage',
            )}
            data={series.V09}
            yFormatter={bytesFormatter}
            colorMap={valkeyMemColors}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.connectedClients',
              'Connected / Blocked Clients',
            )}
            data={series.V10}
            yFormatter={intFormatter}
            colorMap={valkeyClientColors}
          />
        </PanelCol>
      </Row>

      {/* R5 — Keyspace */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.itemsPerDb',
              'Total Items per DB',
            )}
            data={series.V11}
            yFormatter={intFormatter}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.expiringKeys',
              'Expiring vs Not-Expiring Keys',
            )}
            data={series.V12}
            yFormatter={intFormatter}
            colorMap={valkeyKeyColors}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.expiredEvicted',
              'Expired / Evicted Keys',
            )}
            data={series.V13}
            yFormatter={opsFormatter}
            colorMap={valkeyKeyTtlColors}
          />
        </PanelCol>
      </Row>

      {/* R6 — Errors */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={24}>
          <TimeSeriesPanel
            title={t(
              'pages.valkeyMonitor.panel.rejectedConnections',
              'Rejected Connections',
            )}
            data={series.V14}
            yFormatter={opsFormatter}
            colorMap={{
              series: CHART_COLORS.error,
              Evicted: CHART_COLORS.error,
            }}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default ValkeyDashboard;
