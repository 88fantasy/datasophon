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
import { Alert, Row, Select, Tabs } from 'antd';
import { type FC, useCallback, useMemo, useState } from 'react';
import { formatBytes, formatCompact } from '../_shared/charts/formatters';
import { selectionsToRegex } from '../_shared/charts/promql';
import DashboardToolbar, {
  type RefreshInterval,
  type TimeRange,
} from '../_shared/DashboardToolbar';
import { MONITOR_ROW_GUTTER } from '../_shared/layout';
import MonitorDashboardLayout from '../_shared/MonitorDashboardLayout';
import PanelCol from '../_shared/PanelCol';
import StatPanel from '../_shared/panels/StatPanel';
import TimeSeriesPanel, {
  baseSeriesLabel,
} from '../_shared/panels/TimeSeriesPanel';
import { useSeaTunnelDashboard } from './hooks/useSeaTunnelDashboard';
import {
  getSeaTunnelSegmentPanelIds,
  PANEL_QUERIES,
  SEATUNNEL_JOB_BY_SEGMENT,
  type SeaTunnelDashboardSegment,
} from './panelQueries';

const PANEL_TITLES: Record<string, string> = {
  'ST-M01': 'masterNodes',
  'ST-M02': 'runningJobs',
  'ST-M03': 'jobStates',
  'ST-M04': 'jobThreadPool',
  'ST-M05': 'slotRequestRate',
  'ST-W01': 'workerNodes',
  'ST-W02': 'metricReports',
  'ST-W03': 'reportLatency',
  'ST-C01': 'hazelcastMembers',
  'ST-C02': 'partitionSafety',
  'ST-C03': 'executorQueue',
  'ST-C04': 'heapMemory',
  'ST-C05': 'nonHeapDirect',
  'ST-C06': 'gcCount',
  'ST-C07': 'gcTime',
  'ST-C08': 'threads',
  'ST-C09': 'cpuCores',
};

export interface SeaTunnelDashboardProps {
  clusterId: number;
  embedded?: boolean;
}

const SeaTunnelDashboard: FC<SeaTunnelDashboardProps> = ({
  clusterId,
  embedded = false,
}) => {
  const intl = useIntl();
  const t = (key: string) =>
    intl.formatMessage({ id: `pages.seatunnelMonitor.${key}` });
  const [activeSegment, setActiveSegment] =
    useState<SeaTunnelDashboardSegment>('master');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] =
    useState<RefreshInterval>('30s');
  const [refreshKey, setRefreshKey] = useState(0);
  const instance = useMemo(
    () => selectionsToRegex(selectedInstances),
    [selectedInstances],
  );
  const handleRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);
  const handleSegmentChange = useCallback((key: string) => {
    setActiveSegment(key as SeaTunnelDashboardSegment);
    setSelectedInstances([]);
  }, []);
  const { instant, series, instances, loading, error, failedPanelIds } =
    useSeaTunnelDashboard({
      activeSegment,
      instance,
      timeRange,
      clusterId,
      refreshKey,
    });
  const panelIds = getSeaTunnelSegmentPanelIds(activeSegment);

  return (
    <MonitorDashboardLayout
      title={t('title')}
      embedded={embedded}
      toolbar={
        <DashboardToolbar
          timeRange={timeRange}
          onTimeRangeChange={setTimeRange}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={setRefreshInterval}
          onRefresh={handleRefresh}
        >
          <Select
            mode="multiple"
            placeholder={t('instance')}
            value={selectedInstances}
            onChange={setSelectedInstances}
            options={instances.map((value) => ({ label: value, value }))}
            style={{ minWidth: 210 }}
            maxTagCount="responsive"
          />
        </DashboardToolbar>
      }
      meta={`job=~"${SEATUNNEL_JOB_BY_SEGMENT[activeSegment]}" · instance=~"${instance}" · range=${timeRange}`}
      loading={loading}
    >
      <Tabs
        activeKey={activeSegment}
        onChange={handleSegmentChange}
        items={[
          { key: 'master', label: 'Master' },
          { key: 'worker', label: 'Worker' },
        ]}
      />
      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          style={{ marginBottom: 16 }}
        />
      )}
      {failedPanelIds.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={intl.formatMessage(
            { id: 'pages.seatunnelMonitor.partialFailure' },
            { panels: failedPanelIds.join(', ') },
          )}
          style={{ marginBottom: 16 }}
        />
      )}
      <Row gutter={MONITOR_ROW_GUTTER}>
        {panelIds.flatMap((panelId) => {
          const descriptor = PANEL_QUERIES[panelId];
          const panelData = series[panelId] ?? [];

          if (panelId === 'ST-C10') {
            return [
              <PanelCol span={8} key="ST-C10-RSS">
                <TimeSeriesPanel
                  title="RSS"
                  data={panelData.filter(
                    (point) => baseSeriesLabel(point.series) === 'RSS',
                  )}
                  yFormatter={formatBytes}
                />
              </PanelCol>,
              <PanelCol span={8} key="ST-C10-FD">
                <TimeSeriesPanel
                  title={t('fileDescriptors')}
                  data={panelData.filter(
                    (point) => baseSeriesLabel(point.series) !== 'RSS',
                  )}
                  yFormatter={formatCompact}
                />
              </PanelCol>,
            ];
          }

          const isStat =
            descriptor.type === 'node-count' || descriptor.type === 'instant';

          return (
            <PanelCol span={8} key={panelId}>
              {isStat ? (
                <StatPanel
                  title={t(`panel.${PANEL_TITLES[panelId]}`)}
                  value={instant[panelId] ?? Number.NaN}
                />
              ) : (
                <TimeSeriesPanel
                  title={t(`panel.${PANEL_TITLES[panelId]}`)}
                  data={panelData}
                  yFormatter={
                    panelId === 'ST-C04' || panelId === 'ST-C05'
                      ? formatBytes
                      : undefined
                  }
                />
              )}
            </PanelCol>
          );
        })}
      </Row>
    </MonitorDashboardLayout>
  );
};

export default SeaTunnelDashboard;
