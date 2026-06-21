import { Line } from '@ant-design/plots';
import { Empty } from 'antd';
import dayjs from 'dayjs';
import type { FC } from 'react';
import { CHART_COLORS } from '../charts/formatters';
import MonitorPanelCard from '../MonitorPanelCard';
import useStyles from '../monitorStyles';
import type { TimeSeriesPoint } from '../types';

interface TimeSeriesPanelProps {
  title: string;
  data?: TimeSeriesPoint[];
  height?: number;
  yFormatter?: (value: number) => string;
  tooltipFormatter?: (value: number) => string;
  colorMap?: Record<string, string>;
  thresholdLines?: Array<{ value: number; label?: string; color?: string }>;
}

const defaultFormatter = (value: number) => value.toFixed(2);

const TimeSeriesPanel: FC<TimeSeriesPanelProps> = ({
  title,
  data = [],
  height = 180,
  yFormatter = defaultFormatter,
  tooltipFormatter = yFormatter,
  colorMap,
  thresholdLines,
}) => {
  const { styles } = useStyles();

  if (!data.length) {
    return (
      <MonitorPanelCard title={title}>
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          className={styles.empty}
          style={{ height }}
        />
      </MonitorPanelCard>
    );
  }

  const seriesNames = [...new Set(data.map((point) => point.series))];
  const colorRange = seriesNames.map(
    (name, index) =>
      colorMap?.[name] ??
      CHART_COLORS.series[index % CHART_COLORS.series.length],
  );

  const chartKey =
    data.length > 0 ? `${data[0].time}-${data[data.length - 1].time}` : 'empty';

  return (
    <MonitorPanelCard title={title}>
      <Line
        key={chartKey}
        data={data}
        xField="time"
        yField="value"
        seriesField="series"
        height={height}
        smooth
        axis={{
          x: {
            labelFormatter: (value: number) => dayjs(value).format('HH:mm'),
            tickCount: 5,
          },
          y: { labelFormatter: yFormatter },
        }}
        scale={{
          x: { type: 'time' },
          color: { type: 'ordinal', range: colorRange },
        }}
        legend={{ position: 'top-right' }}
        annotations={thresholdLines?.map((line) => ({
          type: 'lineY',
          y: line.value,
          style: {
            stroke: line.color ?? CHART_COLORS.warning,
            lineDash: [4, 4],
          },
          labelText: line.label,
          labelFill: line.color ?? CHART_COLORS.warning,
        }))}
        tooltip={{
          title: (point: TimeSeriesPoint) =>
            dayjs(point.time).format('HH:mm:ss'),
          items: [
            (point: TimeSeriesPoint) => ({
              name: point.series,
              value: tooltipFormatter(point.value),
            }),
          ],
        }}
      />
    </MonitorPanelCard>
  );
};

export default TimeSeriesPanel;
