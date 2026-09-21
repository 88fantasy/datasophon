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

import {
  AlertOutlined,
  BellOutlined,
  DesktopOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import { Alert, App, Row, Typography } from 'antd';
import { type FC, useCallback, useContext, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import {
  CHART_COLORS,
  formatBytes,
} from '../../monitor/_shared/charts/formatters';
import DashboardToolbar, {
  type RefreshInterval,
  type TimeRange,
} from '../../monitor/_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../../monitor/_shared/layout';
import MonitorDashboardLayout from '../../monitor/_shared/MonitorDashboardLayout';
import PanelCol from '../../monitor/_shared/PanelCol';
import TimeSeriesPanel from '../../monitor/_shared/panels/TimeSeriesPanel';
import { useClusterOtelPanels } from './hooks/useClusterOtelPanels';
import { useClusterSummary } from './hooks/useClusterSummary';
import AlertTrendPanel from './panels/AlertTrendPanel';
import ClusterProfilePanel from './panels/ClusterProfilePanel';
import ClusterStatCard from './panels/ClusterStatCard';
import RecentAlertsPanel from './panels/RecentAlertsPanel';
import ResourceGaugePanel from './panels/ResourceGaugePanel';
import ServiceHealthPanel from './panels/ServiceHealthPanel';

const percentFormatter = (value: number) => `${value.toFixed(1)}%`;
const rateFormatter = (value: number) => `${formatBytes(value)}/s`;

const ClusterDashboard: FC = () => {
  const { message } = App.useApp();
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;

  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string) => intl.formatMessage({ id });
  const panelTitle = (key: string) => t(`pages.clusterDashboard.panel.${key}`);

  const handleRefresh = useCallback(() => setRefreshKey((key) => key + 1), []);

  const otel = useClusterOtelPanels({ clusterId, timeRange, refreshKey });
  const {
    summary,
    recentAlerts,
    loading: summaryLoading,
    error: summaryError,
    summaryFailed,
    alertsFailed,
  } = useClusterSummary({ clusterId, refreshKey });

  const openService = (serviceName: string) => {
    const service = ctx?.serviceList?.find(
      (item) => item.serviceName === serviceName,
    );
    if (service) history.push(`/cluster/${clusterId}/service/${service.id}`);
    else message.warning('未找到对应服务实例，请刷新后重试');
  };
  const metricsFailed = Boolean(otel.error || otel.failedPanelIds.length);
  const metricNames: Record<string, string> = {
    'CO-CPU': panelTitle('cpu'),
    'CO-NET': panelTitle('network'),
    'CO-MEM-PCT': t('pages.clusterDashboard.resource.memory'),
    'CO-DISK-PCT': t('pages.clusterDashboard.resource.disk'),
  };

  const stats = summary?.stats;
  const changeLabel = t('pages.clusterDashboard.stat.changeLabel');
  const alertLevelLabels = {
    warning: t('pages.clusterDashboard.alertLevel.warning'),
    exception: t('pages.clusterDashboard.alertLevel.exception'),
  };

  return (
    <MonitorDashboardLayout
      embedded
      title={t('pages.clusterDashboard.title')}
      toolbar={
        <DashboardToolbar
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          onRefresh={handleRefresh}
        />
      }
      loading={summaryLoading || otel.loading}
    >
      {metricsFailed && (
        <Alert
          type="warning"
          showIcon
          title="监控指标查询失败"
          description={`受影响指标：${otel.failedPanelIds.map((id) => metricNames[id] ?? id).join('、') || '全部'}。${otel.error ?? ''} 当前显示的数据可能不完整或已过期，请刷新重试。`}
          action={
            <Typography.Link onClick={handleRefresh}>重试</Typography.Link>
          }
          style={{ marginBottom: 16 }}
        />
      )}
      <Typography.Paragraph type="secondary">
        监控指标最近完整查询成功：
        {otel.lastSuccessAt
          ? new Date(otel.lastSuccessAt).toLocaleString()
          : '尚无成功记录'}
      </Typography.Paragraph>
      {summaryError && (
        <Alert
          type="warning"
          showIcon
          title={t('pages.clusterDashboard.partialLoadError')}
          description={`${summaryError}。部分数据未更新，保留的数据可能已过期。`}
          action={
            <Typography.Link onClick={handleRefresh}>重试</Typography.Link>
          }
          style={{ marginBottom: 16 }}
        />
      )}
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={6}>
          <ClusterStatCard
            title={t('pages.clusterDashboard.stat.hostTotal')}
            value={stats?.hostTotal ?? Number.NaN}
            color={CHART_COLORS.primary}
            icon={<DesktopOutlined />}
            delta={stats?.hostDelta}
            deltaLabel={changeLabel}
          />
        </PanelCol>
        <PanelCol span={6}>
          <ClusterStatCard
            title={t('pages.clusterDashboard.stat.serviceTotal')}
            value={stats?.serviceTotal ?? Number.NaN}
            color={CHART_COLORS.success}
            icon={<InboxOutlined />}
            delta={stats?.serviceDelta}
            deltaLabel={changeLabel}
          />
        </PanelCol>
        <PanelCol span={6}>
          <ClusterStatCard
            title={t('pages.clusterDashboard.stat.alertTotal')}
            value={stats?.alertTotal ?? Number.NaN}
            color={CHART_COLORS.warning}
            icon={<AlertOutlined />}
            delta={stats?.alertDelta}
            deltaLabel={changeLabel}
            positiveIsGood={false}
          />
        </PanelCol>
        <PanelCol span={6}>
          <ClusterStatCard
            title={t('pages.clusterDashboard.stat.criticalAlertTotal')}
            value={stats?.criticalAlertTotal ?? Number.NaN}
            color={CHART_COLORS.error}
            icon={<BellOutlined />}
            delta={stats?.criticalAlertDelta}
            deltaLabel={changeLabel}
            positiveIsGood={false}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <RecentAlertsPanel
            title={panelTitle('recentAlerts')}
            levelLabels={alertLevelLabels}
            columnLabels={{
              level: t('pages.clusterDashboard.column.level'),
              target: t('pages.clusterDashboard.column.target'),
              hostname: t('pages.clusterDashboard.column.hostname'),
              createTime: t('pages.clusterDashboard.column.createTime'),
            }}
            emptyText={
              alertsFailed
                ? '告警数据查询失败，请重试'
                : t('pages.clusterDashboard.emptyText.alerts')
            }
            viewAllLabel={t('pages.clusterDashboard.viewAll')}
            data={recentAlerts}
            onOpenService={(instanceId) =>
              history.push(`/cluster/${clusterId}/service/${instanceId}`)
            }
            onViewAll={() =>
              history.push(`/cluster/${clusterId}/alarm?tab=history`)
            }
          />
        </PanelCol>
        <PanelCol span={12}>
          <ServiceHealthPanel
            title={panelTitle('serviceHealth')}
            columnLabels={{
              service: t('pages.clusterDashboard.column.service'),
              roles: t('pages.clusterDashboard.column.roles'),
              health: t('pages.clusterDashboard.column.health'),
              alertNum: t('pages.clusterDashboard.column.alertNum'),
              state: t('pages.clusterDashboard.column.state'),
            }}
            emptyText={
              summaryFailed
                ? '服务数据查询失败，请重试'
                : t('pages.clusterDashboard.emptyText.services')
            }
            viewMoreLabel={t('pages.clusterDashboard.viewMore')}
            data={summary?.serviceHealth ?? []}
            onOpenService={openService}
            onViewMore={() => history.push(`/cluster/${clusterId}/service`)}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={14}>
          <TimeSeriesPanel
            title={panelTitle('cpu')}
            data={otel.cpuSeries}
            loading={otel.loading}
            error={
              Boolean(otel.error) || otel.failedPanelIds.includes('CO-CPU')
            }
            yFormatter={percentFormatter}
          />
        </PanelCol>
        <PanelCol span={10}>
          <ResourceGaugePanel
            title={panelTitle('resourceUsage')}
            items={[
              {
                label: t('pages.clusterDashboard.resource.cpu'),
                percent: otel.cpuPercent,
              },
              {
                label: t('pages.clusterDashboard.resource.memory'),
                percent: otel.memoryPercent,
              },
              {
                label: t('pages.clusterDashboard.resource.disk'),
                percent: otel.diskPercent,
              },
            ]}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={10}>
          <TimeSeriesPanel
            title={panelTitle('network')}
            data={otel.networkSeries}
            loading={otel.loading}
            error={
              Boolean(otel.error) || otel.failedPanelIds.includes('CO-NET')
            }
            yFormatter={rateFormatter}
          />
        </PanelCol>
        <PanelCol span={14}>
          <AlertTrendPanel
            title={panelTitle('alertTrend')}
            warningLabel={alertLevelLabels.warning}
            exceptionLabel={alertLevelLabels.exception}
            data={summary?.alertTrend ?? []}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={24}>
          <ClusterProfilePanel
            title={panelTitle('profile')}
            labels={{
              frame: t('pages.clusterDashboard.profile.frame'),
              cpuArchitecture: t(
                'pages.clusterDashboard.profile.cpuArchitecture',
              ),
              nodeCount: t('pages.clusterDashboard.profile.nodeCount'),
              totalCores: t('pages.clusterDashboard.profile.totalCores'),
              totalMem: t('pages.clusterDashboard.profile.totalMem'),
              totalDisk: t('pages.clusterDashboard.profile.totalDisk'),
              clusterState: t('pages.clusterDashboard.profile.clusterState'),
              createTime: t('pages.clusterDashboard.profile.createTime'),
            }}
            profile={summary?.profile}
          />
        </PanelCol>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default ClusterDashboard;
