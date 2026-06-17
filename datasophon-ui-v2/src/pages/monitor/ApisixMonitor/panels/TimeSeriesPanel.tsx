import { Line } from '@ant-design/plots';
import { Card, Empty } from 'antd';
import dayjs from 'dayjs';
import type { FC } from 'react';
import type { TimeSeriesPoint } from '../mock/apisixMockData';

interface TimeSeriesPanelProps {
  title: string;
  data: TimeSeriesPoint[];
  height?: number;
  unit?: string;
  /** 系列名 → 颜色，不传则 G2 自动分配 */
  colorMap?: Record<string, string>;
}

const TimeSeriesPanel: FC<TimeSeriesPanelProps> = ({
  title,
  data,
  height = 180,
  unit,
  colorMap,
}) => {
  if (!data.length) {
    return (
      <Card title={title} variant="borderless">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ height }} />
      </Card>
    );
  }

  const seriesNames = [...new Set(data.map((d) => d.series))];
  const colorRange = colorMap
    ? seriesNames.map((n) => colorMap[n] ?? '#1677ff')
    : undefined;

  return (
    <Card title={title} variant="borderless">
      <Line
        data={data}
        xField="time"
        yField="value"
        seriesField="series"
        height={height}
        smooth
        axis={{
          x: {
            labelFormatter: (v: number) => dayjs(v).format('HH:mm'),
            tickCount: 5,
          },
          y: unit ? { labelFormatter: (v: number) => `${v}${unit}` } : {},
        }}
        scale={{
          x: { type: 'time' },
          ...(colorRange
            ? { color: { type: 'ordinal', range: colorRange } }
            : {}),
        }}
        legend={{ position: 'top-right' }}
        tooltip={{
          title: (d: TimeSeriesPoint) => dayjs(d.time).format('HH:mm:ss'),
          items: [
            (d: TimeSeriesPoint) => ({
              name: d.series,
              value: unit ? `${d.value.toFixed(2)}${unit}` : d.value.toFixed(2),
            }),
          ],
        }}
      />
    </Card>
  );
};

export default TimeSeriesPanel;
