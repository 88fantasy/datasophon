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
import { Badge, Row, Statistic } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import { CHART_COLORS, colorByThreshold } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type { RefreshInterval, TimeRange } from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import MonitorPanelCard from '../_shared/MonitorPanelCard';
import PanelCol from '../_shared/PanelCol';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import ZKDashboardToolbar from '../ZooKeeperMonitor/toolbar/ZKDashboardToolbar';
import { useApisixDashboard } from './hooks/useApisixDashboard';

const BANDWIDTH_COLORS: Record<string, string> = {
  ingress: CHART_COLORS.success,
  egress: CHART_COLORS.primary,
};

const CONNECTION_COLORS: Record<string, string> = {
  active: CHART_COLORS.primary,
  reading: '#13c2c2',
  writing: CHART_COLORS.warning,
  waiting: '#8c8c8c',
};

const LATENCY_COLORS: Record<string, string> = {
  p90: CHART_COLORS.primary,
  p95: CHART_COLORS.warning,
  p99: CHART_COLORS.error,
};

const DICT_COLORS: Record<string, string> = {
  'prometheus-metrics': CHART_COLORS.error,
  'plugin-limit-req': CHART_COLORS.primary,
  'plugin-limit-conn': CHART_COLORS.warning,
  'balancer-ewma': CHART_COLORS.success,
};

const rpsFormatter = (value: number) => `${value.toFixed(2)} req/s`;
const msFormatter = (value: number) => `${value.toFixed(1)} ms`;
const percentFormatter = (value: number) => `${value.toFixed(1)}%`;

interface StatusStatPanelProps {
  title: string;
  value: number;
}

// Nginx 错误计数:0 正常,>=1 告警(仿 NexusMonitor 的 StatusStatPanel 就地定义手法)
// value 为非有限值(NaN/Infinity，来自 vectorToScalar 对空结果的约定)时不能当作
// "无错误"渲染成绿色 OK——那会把 Doris 无数据/查询失败误判为健康，须单独显示为无数据状态。
const StatusStatPanel: FC<StatusStatPanelProps> = ({ title, value }) => {
  const noData = !Number.isFinite(value);
  const hasError = !noData && value >= 1;
  const color = noData
    ? '#8c8c8c'
    : hasError
      ? CHART_COLORS.warning
      : CHART_COLORS.success;

  return (
    <MonitorPanelCard compact>
      <Statistic
        title={title}
        value={value}
        formatter={() => (
          <Badge
            status={noData ? 'default' : hasError ? 'warning' : 'success'}
            text={noData ? 'No Data' : hasError ? `${value} errors` : 'OK'}
          />
        )}
        styles={{
          content: { color, fontSize: 24, fontWeight: 600 },
          value: { color },
        }}
      />
    </MonitorPanelCard>
  );
};

export interface ApisixDashboardProps {
  clusterId: number;
}

const ApisixDashboard: FC<ApisixDashboardProps> = ({ clusterId }) => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });
  const panelTitle = (id: string) => t(`pages.apisixMonitor.panel.${id}`);
  const title = t('pages.apisixMonitor.title');

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

  const { instant, series, instances, jobs, loading } = useApisixDashboard({
    variables,
    timeRange,
    clusterId,
    refreshKey,
  });

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={title}
      toolbar={
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
      }
      meta={
        <>
          {'instance=~"'}
          {variables.instance}
          {'" job=~"'}
          {variables.job}
          {'"'}
          {' · '}
          range={timeRange}
        </>
      }
      loading={loading}
    >
      {/* R1 — 摘要统计 */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={6}>
          <StatPanel
            title={panelTitle('A01')}
            value={instant.totalRequests}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={panelTitle('A02')}
            value={instant.acceptedConnections}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={panelTitle('A03')}
            value={instant.handledConnections}
            color={CHART_COLORS.primary}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={panelTitle('A04')}
            value={instant.activeConnections}
            color={colorByThreshold(instant.activeConnections, [100, 500])}
          />
        </PanelCol>
      </Row>

      {/* R2 — 状态指示 */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={24}>
          <StatusStatPanel
            title={panelTitle('A05')}
            value={instant.nginxMetricErrors}
          />
        </PanelCol>
      </Row>

      {/* R3 — 流量 */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('A06')}
            data={series.A06}
            yFormatter={rpsFormatter}
            tooltipFormatter={rpsFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <AreaPanel
            title={panelTitle('A07')}
            data={series.A07}
            stack
            colorMap={BANDWIDTH_COLORS}
          />
        </PanelCol>
      </Row>

      {/* R4 — 延迟(p90/p95/p99) */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title={panelTitle('A08')}
            data={series.A08}
            yFormatter={msFormatter}
            tooltipFormatter={msFormatter}
            colorMap={LATENCY_COLORS}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title={panelTitle('A09')}
            data={series.A09}
            yFormatter={msFormatter}
            tooltipFormatter={msFormatter}
            colorMap={LATENCY_COLORS}
          />
        </PanelCol>
        <PanelCol span={8}>
          <TimeSeriesPanel
            title={panelTitle('A10')}
            data={series.A10}
            yFormatter={msFormatter}
            tooltipFormatter={msFormatter}
            colorMap={LATENCY_COLORS}
          />
        </PanelCol>
      </Row>

      {/* R5 — Nginx 连接状态 & 共享字典剩余空间占比 */}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <AreaPanel
            title={panelTitle('A11')}
            data={series.A11}
            stack
            colorMap={CONNECTION_COLORS}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={panelTitle('A12')}
            data={series.A12}
            yFormatter={percentFormatter}
            tooltipFormatter={percentFormatter}
            colorMap={DICT_COLORS}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default ApisixDashboard;
