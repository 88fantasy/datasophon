import { useIntl } from '@umijs/max';
import { Col, Row, Select } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import {
  CHART_COLORS,
  colorByThreshold,
  formatBytes,
  formatCompact,
} from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import type {
  PrometheusInterval,
  RefreshInterval,
  TimeRange,
} from '../_shared/DashboardToolbar';
import DashboardToolbar from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import AreaPanel from '../_shared/panels/AreaPanel';
import StatPanel from '../_shared/panels/StatPanel';
import TablePanel from '../_shared/panels/TablePanel';
import TimeSeriesPanel from '../_shared/panels/TimeSeriesPanel';
import { usePrometheusDashboard } from './hooks/usePrometheusDashboard';

const createdRemovedColors = {
  created: CHART_COLORS.success,
  removed: CHART_COLORS.error,
};

const ruleEvaluatorColors = {
  total: CHART_COLORS.primary,
  missed: CHART_COLORS.error,
  skipped: CHART_COLORS.warning,
};

const errorColors = {
  conn_failed: '#ff4d4f',
  rule_eval_failed: '#ff7a45',
  scrape_sample_limit: '#fa541c',
  tsdb_reload_failed: '#cf1322',
  tsdb_compaction_failed: '#a8071a',
  sample_out_of_order: '#eb2f96',
};

const notificationColors = {
  notifications: '#722ed1',
};

const integerFormatter = (value: number) => value.toFixed(0);
const secondFormatter = (value: number) => `${value.toFixed(3)}s`;
const fourDecimalSecondFormatter = (value: number) => `${value.toFixed(4)}s`;
const millisecondFormatter = (value: number) => `${value.toFixed(1)}ms`;
const twoDecimalMillisecondFormatter = (value: number) =>
  `${value.toFixed(2)}ms`;
const sampleFormatter = (value: number) => `${value.toFixed(0)}/s`;
const rateFormatter = (value: number) => `${value.toFixed(4)}s/s`;
const iterationFormatter = (value: number) => `${value.toFixed(2)}/s`;
const notificationFormatter = (value: number) => `${value.toFixed(2)}/s`;
const minuteFormatter = (value: number) => `${value.toFixed(1)}min`;
const upnessFormatter = (value: number) => value.toFixed(0);

const PrometheusDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [interval, setInterval] = useState<PrometheusInterval>('5m');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const intl = useIntl();
  const t = (id: string, values?: Record<string, string>) =>
    intl.formatMessage({ id }, values);

  const variables = useMemo(
    () => ({
      instance:
        selectedInstances.length > 0
          ? selectionsToRegex(selectedInstances)
          : '.+',
      job: selectedJobs.length > 0 ? selectionsToRegex(selectedJobs) : '.+',
      interval,
    }),
    [interval, selectedInstances, selectedJobs],
  );

  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  const { instant, series, down, instances, jobs, loading } =
    usePrometheusDashboard({
      variables,
      timeRange,
      clusterId: 1,
      refreshKey,
    });

  return (
    <MonitorDashboardLayout
      key={refreshKey}
      title={t('pages.prometheusMonitor.title')}
      toolbar={
        <DashboardToolbar
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          interval={interval}
          onIntervalChange={setInterval}
          onRefresh={handleRefresh}
        >
          <Select
            mode="multiple"
            placeholder={t('pages.prometheusMonitor.toolbar.instance')}
            value={selectedInstances}
            onChange={setSelectedInstances}
            options={instances.map((value) => ({ label: value, value }))}
            style={{ minWidth: 210 }}
            maxTagCount="responsive"
          />
          <Select
            mode="multiple"
            placeholder="Job"
            value={selectedJobs}
            onChange={setSelectedJobs}
            options={jobs.map((value) => ({ label: value, value }))}
            style={{ minWidth: 190 }}
            maxTagCount="responsive"
          />
        </DashboardToolbar>
      }
      meta={
        <>
          instance=~&quot;{variables.instance}&quot; job=~&quot;{variables.job}&quot;
          {' · '}
          range={timeRange} interval={variables.interval}
        </>
      }
      loading={loading}
    >
      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={4}>
          <StatPanel
            title={t('pages.prometheusMonitor.panel.uptime', { interval })}
            value={instant.uptime}
            suffix="%"
            precision={1}
            color={colorByThreshold(instant.uptime, [90, 99], {
              reverse: true,
            })}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.prometheusMonitor.panel.totalSeries')}
            value={instant.totalSeries}
            color={colorByThreshold(
              instant.totalSeries,
              [1_000_000, 2_000_000],
            )}
            formatter={formatCompact}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.prometheusMonitor.panel.memoryChunks')}
            value={instant.memoryChunks}
            color={CHART_COLORS.primary}
            formatter={formatCompact}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.prometheusMonitor.panel.reloadFailures', {
              interval,
            })}
            value={instant.reloadFailures}
            color={colorByThreshold(instant.reloadFailures, [1, 10])}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.prometheusMonitor.panel.missedIterations', {
              interval,
            })}
            value={instant.missedIterations}
            color={colorByThreshold(instant.missedIterations, [1, 10])}
          />
        </Col>
        <Col span={4}>
          <StatPanel
            title={t('pages.prometheusMonitor.panel.skippedScrapes', {
              interval,
            })}
            value={instant.skippedScrapes}
            color={colorByThreshold(instant.skippedScrapes, [1, 10])}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={8}>
          <TablePanel
            title={t('pages.prometheusMonitor.panel.currentlyDown')}
            data={down}
          />
        </Col>
        <Col span={16}>
          <AreaPanel
            title={t('pages.prometheusMonitor.panel.upness')}
            data={series.P08}
            stack
            yFormatter={upnessFormatter}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.scrapeDuration')}
            data={series.P09}
            yFormatter={secondFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.targetSync')}
            data={series.P10}
            yFormatter={millisecondFormatter}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.scrapeSyncTotal')}
            data={series.P11}
            yFormatter={integerFormatter}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.rejectedScrapes')}
            data={series.P12}
            yFormatter={integerFormatter}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.seriesCount')}
            data={series.P13}
            yFormatter={integerFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.seriesCreatedRemoved')}
            data={series.P14}
            yFormatter={integerFormatter}
            colorMap={createdRemovedColors}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.appendedSamples')}
            data={series.P15}
            yFormatter={sampleFormatter}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.storageChunks')}
            data={series.P16}
            yFormatter={integerFormatter}
          />
        </Col>
        <Col span={8}>
          <AreaPanel
            title={t('pages.prometheusMonitor.panel.goMemory')}
            data={series.P17}
            yFormatter={formatBytes}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.gcRate')}
            data={series.P18}
            yFormatter={rateFormatter}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.ruleEvalIterations')}
            data={series.P19}
            yFormatter={iterationFormatter}
            colorMap={ruleEvaluatorColors}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.ruleEvalDuration')}
            data={series.P20}
            yFormatter={twoDecimalMillisecondFormatter}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.queryDuration')}
            data={series.P21}
            yFormatter={fourDecimalSecondFormatter}
          />
        </Col>
      </Row>

      <Row gutter={MONITOR_ROW_GUTTER}>
        <Col span={12}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.failuresAndErrors')}
            data={series.P22}
            yFormatter={integerFormatter}
            colorMap={errorColors}
          />
        </Col>
        <Col span={6}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.notificationsSent')}
            data={series.P23}
            yFormatter={notificationFormatter}
            colorMap={notificationColors}
          />
        </Col>
        <Col span={6}>
          <TimeSeriesPanel
            title={t('pages.prometheusMonitor.panel.configReloadMinutes')}
            data={series.P24}
            yFormatter={minuteFormatter}
          />
        </Col>
      </Row>
    </MonitorDashboardLayout>
  );
};

export default PrometheusDashboard;
