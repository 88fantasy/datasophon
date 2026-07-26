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

import { Column } from '@ant-design/plots';
import { Empty } from 'antd';
import type { FC } from 'react';
import { CHART_COLORS } from '../../../monitor/_shared/charts/formatters';
import MonitorPanelCard from '../../../monitor/_shared/MonitorPanelCard';
import useStyles from '../../../monitor/_shared/monitorStyles';

interface AlertTrendChartRow {
  day: string;
  level: string;
  value: number;
}

interface AlertTrendPanelProps {
  title: string;
  warningLabel: string;
  exceptionLabel: string;
  data: DATASOPHON.ClusterDashboardAlertTrendPoint[];
  height?: number;
}

/** 参考图是 4 级堆叠柱；本项目 AlertLevel 只有 warning/exception 两级，此处按两级堆叠。 */
const AlertTrendPanel: FC<AlertTrendPanelProps> = ({
  title,
  warningLabel,
  exceptionLabel,
  data,
  height = 220,
}) => {
  const { styles } = useStyles();

  if (!data.length) {
    return (
      <MonitorPanelCard title={title}>
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          className={styles.empty}
          style={{ height }}
          description="当前时间范围内暂无告警趋势"
        />
      </MonitorPanelCard>
    );
  }

  const chartData: AlertTrendChartRow[] = data.flatMap((point) => [
    { day: point.day, level: warningLabel, value: point.warning },
    { day: point.day, level: exceptionLabel, value: point.exception },
  ]);

  return (
    <MonitorPanelCard title={title}>
      <Column
        data={chartData}
        xField="day"
        yField="value"
        colorField="level"
        stack
        height={height}
        scale={{
          color: {
            type: 'ordinal',
            range: [CHART_COLORS.warning, CHART_COLORS.error],
          },
        }}
        axis={{ x: { title: false }, y: { title: false } }}
        legend={{ position: 'top-right' }}
      />
    </MonitorPanelCard>
  );
};

export default AlertTrendPanel;
