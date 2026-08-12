/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { useIntl } from '@umijs/max';
import { Alert, Row } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import { CHART_COLORS, colorByThreshold } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import PanelCol from '../_shared/PanelCol';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import {
  gcCollectionRateFormatter,
  gcTimeRateFormatter,
  requestRateFormatter,
} from './formatters';
import { useGravitinoDashboard } from './hooks/useGravitinoDashboard';
import {
  GRAVITINO_JOB_FILTER,
  GRAVITINO_QUEUED_REQUEST_THRESHOLDS,
} from './panelQueries';
import GravitinoDashboardToolbar from './toolbar/GravitinoDashboardToolbar';

const countFormatter = (value: number) => value.toFixed(0);
const millisecondFormatter = (value: number) => `${value.toFixed(2)} ms`;
const percentFormatter = (value: number) => `${value.toFixed(1)}%`;
const bytesFormatter = (value: number) => {
  if (!Number.isFinite(value)) return '-';
  const units = ['B', 'KB', 'MB', 'GB'];
  let scaled = value;
  let index = 0;
  while (scaled >= 1024 && index < units.length - 1) {
    scaled /= 1024;
    index += 1;
  }
  return `${scaled.toFixed(1)} ${units[index]}`;
};

const STATUS_COLORS = {
  '1xx': CHART_COLORS.series[6],
  '2xx': CHART_COLORS.success,
  '3xx': CHART_COLORS.series[4],
  '4xx': CHART_COLORS.warning,
  '5xx': CHART_COLORS.error,
};

const ERROR_STATUS_COLORS = {
  '4xx': CHART_COLORS.warning,
  '5xx': CHART_COLORS.error,
};

const QUANTILE_COLORS = {
  p50: CHART_COLORS.primary,
  p99: CHART_COLORS.warning,
};

const THREAD_COLORS = {
  Busy: CHART_COLORS.error,
  Idle: CHART_COLORS.success,
  Total: CHART_COLORS.primary,
  Max: CHART_COLORS.series[4],
};

const HEALTH_COLORS = {
  Live: CHART_COLORS.primary,
  Ready: CHART_COLORS.success,
};

const CONNECTION_COLORS = {
  Active: CHART_COLORS.error,
  Idle: CHART_COLORS.success,
  Max: CHART_COLORS.series[4],
};

const METADATA_READ_COLORS = {
  'List Metalakes': CHART_COLORS.primary,
  'Get Metalake': CHART_COLORS.success,
};

const CLEANUP_COLORS = {
  'Delete Table Metas': CHART_COLORS.primary,
  'Delete Fileset Versions': CHART_COLORS.warning,
};

const HEAP_COLORS = {
  Used: CHART_COLORS.error,
  Committed: CHART_COLORS.warning,
  Max: CHART_COLORS.series[4],
};

const GC_COLORS = {
  Young: CHART_COLORS.primary,
  Old: CHART_COLORS.warning,
};

const NON_HEAP_COLORS = {
  'Non-Heap': CHART_COLORS.primary,
  Metaspace: CHART_COLORS.warning,
  Direct: CHART_COLORS.series[4],
};

export interface GravitinoDashboardProps {
  clusterId: number;
  embedded?: boolean;
}

const GravitinoDashboard: FC<GravitinoDashboardProps> = ({
  clusterId,
  embedded = false,
}) => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });
  const panelTitle = (id: string) => t(`pages.gravitinoMonitor.panel.${id}`);

  const instance = useMemo(
    () =>
      selectedInstances.length > 0
        ? selectionsToRegex(selectedInstances)
        : '.+',
    [selectedInstances],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, instances, loading, failedPanelIds } =
    useGravitinoDashboard({
      instance,
      timeRange,
      clusterId,
      refreshKey,
    });

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      embedded={embedded}
      toolbar={
        <GravitinoDashboardToolbar
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
      meta={`service_name=~"${GRAVITINO_JOB_FILTER}" · instance=~"${instance}" · range=${timeRange}`}
      loading={loading}
    >
      {failedPanelIds.length > 0 && (
        <Alert
          type="warning"
          showIcon
          title={t('pages.gravitinoMonitor.partialLoad.title')}
          description={intl.formatMessage(
            { id: 'pages.gravitinoMonitor.partialLoad.description' },
            { panels: failedPanelIds.join(', ') },
          )}
          style={{ marginBottom: 16 }}
        />
      )}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('G01')}
            value={instant.nodeCount}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('G02')}
            value={instant.httpQps}
            color={CHART_COLORS.primary}
            formatter={requestRateFormatter}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('G03')}
            value={instant.jettyThreadUsage}
            color={colorByThreshold(instant.jettyThreadUsage, [60, 85])}
            formatter={percentFormatter}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('G04')}
            value={instant.queuedRequests}
            color={colorByThreshold(
              instant.queuedRequests,
              GRAVITINO_QUEUED_REQUEST_THRESHOLDS,
            )}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('G05')}
            value={instant.activeConnections}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('G06')}
            value={instant.heapUsage}
            color={colorByThreshold(instant.heapUsage, [70, 90])}
            formatter={percentFormatter}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G07')}
            data={series.G07}
            yFormatter={requestRateFormatter}
            colorMap={STATUS_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G08')}
            data={series.G08}
            yFormatter={requestRateFormatter}
          />
        </PanelCol>
      </Row>
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G09')}
            data={series.G09}
            yFormatter={requestRateFormatter}
            colorMap={ERROR_STATUS_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G10')}
            data={series.G10}
            yFormatter={millisecondFormatter}
            colorMap={QUANTILE_COLORS}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G11')}
            data={series.G11}
            yFormatter={countFormatter}
            colorMap={THREAD_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G12')}
            data={series.G12}
            yFormatter={requestRateFormatter}
            colorMap={HEALTH_COLORS}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G13')}
            data={series.G13}
            yFormatter={countFormatter}
            colorMap={CONNECTION_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G14')}
            data={series.G14}
            yFormatter={requestRateFormatter}
            colorMap={METADATA_READ_COLORS}
          />
        </PanelCol>
      </Row>
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G15')}
            data={series.G15}
            yFormatter={requestRateFormatter}
            colorMap={METADATA_READ_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G16')}
            data={series.G16}
            yFormatter={requestRateFormatter}
            colorMap={CLEANUP_COLORS}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G17')}
            data={series.G17}
            yFormatter={bytesFormatter}
            colorMap={HEAP_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G18')}
            data={series.G18}
            yFormatter={gcCollectionRateFormatter}
            colorMap={GC_COLORS}
          />
        </PanelCol>
      </Row>
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G19')}
            data={series.G19}
            yFormatter={gcTimeRateFormatter}
            colorMap={GC_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('G20')}
            data={series.G20}
            yFormatter={bytesFormatter}
            colorMap={NON_HEAP_COLORS}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default GravitinoDashboard;
