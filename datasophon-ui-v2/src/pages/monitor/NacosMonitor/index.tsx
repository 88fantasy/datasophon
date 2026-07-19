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
import { Row } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import { CHART_COLORS, colorByThreshold } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import PanelCol from '../_shared/PanelCol';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import { useNacosDashboard } from './hooks/useNacosDashboard';
import { NACOS_JOB_FILTER } from './panelQueries';
import NacosDashboardToolbar from './toolbar/NacosDashboardToolbar';

const countFormatter = (value: number) => value.toFixed(0);
const rateFormatter = (value: number) => `${value.toFixed(2)} req/s`;
const millisecondFormatter = (value: number) => `${value.toFixed(2)} ms`;
const percentFormatter = (value: number) => `${value.toFixed(1)}%`;

const GRPC_COLORS = {
  Active: CHART_COLORS.primary,
  Queued: CHART_COLORS.warning,
};

const CONFIG_COLORS = {
  'Get Config': CHART_COLORS.primary,
  Publish: CHART_COLORS.success,
  'Long Polling': CHART_COLORS.warning,
};

const PUSH_COST_COLORS = {
  Average: CHART_COLORS.primary,
  Maximum: CHART_COLORS.warning,
};

const PUSH_HEALTH_COLORS = {
  'Failed Push': CHART_COLORS.error,
  'Pending Tasks': CHART_COLORS.warning,
};

const CPU_COLORS = {
  System: CHART_COLORS.primary,
  Process: CHART_COLORS.success,
};

const THREAD_COLORS = {
  Live: CHART_COLORS.primary,
  Daemon: CHART_COLORS.success,
};

export interface NacosDashboardProps {
  clusterId: number;
}

const NacosDashboard: FC<NacosDashboardProps> = ({ clusterId }) => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });
  const panelTitle = (id: string) => t(`pages.nacosMonitor.panel.${id}`);

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

  const { instant, series, instances, loading } = useNacosDashboard({
    instance,
    timeRange,
    clusterId,
    refreshKey,
  });

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      toolbar={
        <NacosDashboardToolbar
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
      meta={`service_name=~"${NACOS_JOB_FILTER}" · instance=~"${instance}" · range=${timeRange}`}
      loading={loading}
    >
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('N01')}
            value={instant.nodeCount}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('N02')}
            value={instant.serviceCount}
            color={CHART_COLORS.success}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('N03')}
            value={instant.ipCount}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('N04')}
            value={instant.configCount}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('N05')}
            value={instant.longConnections}
            color={colorByThreshold(instant.longConnections, [1000, 5000])}
          />
        </PanelCol>
        <PanelCol span={4}>
          <StatPanel
            title={panelTitle('N06')}
            value={instant.httpQps}
            color={CHART_COLORS.primary}
            formatter={rateFormatter}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N07')}
            data={series.N07}
            yFormatter={rateFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N08')}
            data={series.N08}
            yFormatter={millisecondFormatter}
          />
        </PanelCol>
      </Row>
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N09')}
            data={series.N09}
            yFormatter={percentFormatter}
            thresholdLines={[
              { value: 1, label: '1%', color: CHART_COLORS.error },
            ]}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N10')}
            data={series.N10}
            yFormatter={millisecondFormatter}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N11')}
            data={series.N11}
            yFormatter={countFormatter}
            colorMap={GRPC_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N12')}
            data={series.N12}
            yFormatter={countFormatter}
            colorMap={CONFIG_COLORS}
          />
        </PanelCol>
      </Row>
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N13')}
            data={series.N13}
            yFormatter={millisecondFormatter}
            colorMap={PUSH_COST_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N14')}
            data={series.N14}
            yFormatter={countFormatter}
            colorMap={PUSH_HEALTH_COLORS}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N15')}
            data={series.N15}
            yFormatter={percentFormatter}
            colorMap={CPU_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N16')}
            data={series.N16}
            yFormatter={percentFormatter}
          />
        </PanelCol>
      </Row>
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N17')}
            data={series.N17}
            yFormatter={countFormatter}
            colorMap={THREAD_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('N18')}
            data={series.N18}
            yFormatter={millisecondFormatter}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default NacosDashboard;
