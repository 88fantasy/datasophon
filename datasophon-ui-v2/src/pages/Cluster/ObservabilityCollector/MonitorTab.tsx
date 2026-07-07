import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Badge, Row } from 'antd';
import { useCallback, useMemo, useRef, useState } from 'react';

import {
  CHART_COLORS,
  formatDuration,
  percentFormatter,
  rateFormatter,
} from '../../monitor/_shared/charts/formatters';
import DashboardToolbar, {
  type RefreshInterval,
  type TimeRange,
} from '../../monitor/_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../../monitor/_shared/layout';
import MonitorDashboardLayout from '../../monitor/_shared/MonitorDashboardLayout';
import MonitorPanelCard from '../../monitor/_shared/MonitorPanelCard';
import PanelCol from '../../monitor/_shared/PanelCol';
import StatPanel from '../../monitor/_shared/panels/StatPanel';
import TimeSeriesPanel from '../../monitor/_shared/panels/TimeSeriesPanel';
import { useCollectorDashboard } from './hooks/useCollectorDashboard';
import { type CollectorNodeMetrics, getCollectorMonitor } from './service';
import { maxProcessUptime, queueUsage, sumFailedDropped } from './summary';

interface MonitorTabProps {
  clusterId: number;
}

const secondsFormatter = (value: number) => formatDuration(value);

const MonitorTab: React.FC<MonitorTabProps> = ({ clusterId }) => {
  const intl = useIntl();
  const actionRef = useRef<ActionType>(null);
  const [nodes, setNodes] = useState<CollectorNodeMetrics[]>([]);
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [refreshKey, setRefreshKey] = useState(0);
  const { series, loading } = useCollectorDashboard({
    clusterId,
    timeRange,
    refreshKey,
  });
  const t = useCallback(
    (id: string, defaultMessage: string) =>
      intl.formatMessage({ id, defaultMessage }),
    [intl],
  );
  const summary = useMemo(
    () => ({
      healthy: nodes.filter((node) => node.healthy).length,
      unhealthy: nodes.filter((node) => !node.healthy).length,
      queueUsage: queueUsage(nodes),
      failedDropped: sumFailedDropped(nodes),
      uptime: maxProcessUptime(nodes),
    }),
    [nodes],
  );
  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
    actionRef.current?.reload();
  }, []);
  const columns: ProColumns<CollectorNodeMetrics>[] = [
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.hostname',
        defaultMessage: 'Collector node',
      }),
      dataIndex: 'hostname',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.health',
        defaultMessage: 'Health',
      }),
      dataIndex: 'healthy',
      render: (_, record) => (
        <Badge
          status={record.healthy ? 'success' : 'error'}
          text={
            record.healthy
              ? intl.formatMessage({
                  id: 'pages.observabilityCollector.healthy',
                  defaultMessage: 'Healthy',
                })
              : record.error ||
                intl.formatMessage({
                  id: 'pages.observabilityCollector.unhealthy',
                  defaultMessage: 'Unhealthy',
                })
          }
        />
      ),
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.queue',
        defaultMessage: 'Queue',
      }),
      search: false,
      render: (_, record) =>
        record.metrics
          ? `${record.metrics.queueSize} / ${record.metrics.queueCapacity}`
          : '-',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.sent',
        defaultMessage: 'Sent',
      }),
      search: false,
      render: (_, record) => record.metrics?.sentTotal ?? '-',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.failed',
        defaultMessage: 'Failed / dropped',
      }),
      search: false,
      render: (_, record) =>
        record.metrics
          ? record.metrics.sendFailedTotal +
            record.metrics.receiverFailedTotal +
            record.metrics.refusedTotal +
            record.metrics.processorDroppedTotal
          : '-',
    },
    {
      title: intl.formatMessage({
        id: 'pages.observabilityCollector.uptime',
        defaultMessage: 'Uptime',
      }),
      search: false,
      render: (_, record) =>
        record.metrics ? formatDuration(record.metrics.processUptime ?? 0) : '-',
    },
  ];

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={t('pages.observabilityCollector.monitorTitle', 'Collector Health')}
      toolbar={
        <DashboardToolbar
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          onRefresh={handleRefresh}
        />
      }
      meta={
        <>
          range={timeRange}
          {' · '}
          Doris OTel metrics
        </>
      }
      loading={loading}
    >
      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.observabilityCollector.healthyNodes', 'Healthy')}
            value={summary.healthy}
            color={CHART_COLORS.success}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.observabilityCollector.unhealthyNodes', 'Unhealthy')}
            value={summary.unhealthy}
            color={
              summary.unhealthy > 0 ? CHART_COLORS.error : CHART_COLORS.success
            }
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.observabilityCollector.queueUsage', 'Queue usage')}
            value={summary.queueUsage}
            color={
              summary.queueUsage >= 80
                ? CHART_COLORS.error
                : CHART_COLORS.primary
            }
            suffix="%"
            precision={1}
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t(
              'pages.observabilityCollector.failedDroppedTotal',
              'Failed / dropped',
            )}
            value={summary.failedDropped}
            color={
              summary.failedDropped > 0
                ? CHART_COLORS.warning
                : CHART_COLORS.success
            }
          />
        </PanelCol>
        <PanelCol span={6}>
          <StatPanel
            title={t('pages.observabilityCollector.processUptime', 'Uptime')}
            value={summary.uptime}
            color={CHART_COLORS.primary}
            formatter={formatDuration}
          />
        </PanelCol>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.observabilityCollector.panel.queueUsage',
              'Queue usage trend',
            )}
            data={series.queueUsage}
            yFormatter={percentFormatter}
            tooltipFormatter={percentFormatter}
            thresholdLines={[{ value: 80, label: '80%' }]}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.observabilityCollector.panel.sentRate',
              'Sent rate',
            )}
            data={series.sentRate}
            yFormatter={rateFormatter}
            tooltipFormatter={rateFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.observabilityCollector.panel.failedRate',
              'Send failed rate',
            )}
            data={series.failedRate}
            yFormatter={rateFormatter}
            tooltipFormatter={rateFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.observabilityCollector.panel.refusedDroppedRate',
              'Refused / dropped rate',
            )}
            data={series.refusedDroppedRate}
            yFormatter={rateFormatter}
            tooltipFormatter={rateFormatter}
          />
        </PanelCol>
        <PanelCol span={12}>
          <TimeSeriesPanel
            title={t(
              'pages.observabilityCollector.panel.uptime',
              'Process uptime',
            )}
            data={series.uptime}
            yFormatter={secondsFormatter}
            tooltipFormatter={secondsFormatter}
          />
        </PanelCol>
      </Row>

      <MonitorPanelCard
        title={t('pages.observabilityCollector.nodeDetails', 'Node details')}
      >
        <ProTable<CollectorNodeMetrics>
          key={clusterId}
          actionRef={actionRef}
          rowKey="hostname"
          columns={columns}
          search={false}
          pagination={false}
          request={async () => {
            if (clusterId <= 0) {
              setNodes([]);
              return { data: [], success: true, total: 0 };
            }
            const result = await getCollectorMonitor(clusterId);
            const data = result.data ?? [];
            setNodes(data);
            return { data, success: result.code === 200, total: data.length };
          }}
        />
      </MonitorPanelCard>
    </MonitorDashboardLayout>
  );
};

export default MonitorTab;
