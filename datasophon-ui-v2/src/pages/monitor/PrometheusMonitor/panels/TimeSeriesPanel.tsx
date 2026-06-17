import { Line } from '@ant-design/plots';
import { Card, Empty } from 'antd';
import dayjs from 'dayjs';
import type { FC } from 'react';
import { CHART_COLORS } from '../../_shared/charts/formatters';
import type { TimeSeriesPoint } from '../../_shared/types';

interface TimeSeriesPanelProps {
  title: string;
  data: TimeSeriesPoint[];
  height?: number;
  yFormatter?: (value: number) => string;
  tooltipFormatter?: (value: number) => string;
  colorMap?: Record<string, string>;
  thresholdLines?: Array<{ value: number; label?: string; color?: string }>;
}

const defaultFormatter = (value: number) => value.toFixed(2);

const TimeSeriesPanel: FC<TimeSeriesPanelProps> = ({
  title,
  data,
  height = 180,
  yFormatter = defaultFormatter,
  tooltipFormatter = yFormatter,
  colorMap,
  thresholdLines,
}) => {
  if (!data.length) {
    return (
      <Card title={title} variant="borderless" style={{ height: '100%' }}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ height }} />
      </Card>
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
    <Card title={title} variant="borderless" style={{ height: '100%' }}>
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
    </Card>
  );
};

export default TimeSeriesPanel;
