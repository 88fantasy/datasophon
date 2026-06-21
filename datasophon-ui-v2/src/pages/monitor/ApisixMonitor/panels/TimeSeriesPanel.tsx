import { Line } from '@ant-design/plots';
import { Empty } from 'antd';
import dayjs from 'dayjs';
import type { FC } from 'react';
import MonitorPanelCard from '../../_shared/MonitorPanelCard';
import useStyles from '../../_shared/monitorStyles';
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

  const seriesNames = [...new Set(data.map((d) => d.series))];
  const colorRange = colorMap
    ? seriesNames.map((n) => colorMap[n] ?? '#1677ff')
    : undefined;

  return (
    <MonitorPanelCard title={title}>
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
    </MonitorPanelCard>
  );
};

export default TimeSeriesPanel;
