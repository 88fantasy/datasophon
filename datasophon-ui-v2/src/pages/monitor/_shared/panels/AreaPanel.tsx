import { Area } from '@ant-design/plots';
import { Empty } from 'antd';
import dayjs from 'dayjs';
import type { FC } from 'react';
import { CHART_COLORS } from '../charts/formatters';
import MonitorPanelCard from '../MonitorPanelCard';
import useStyles from '../monitorStyles';
import type { TimeSeriesPoint } from '../types';

interface AreaPanelProps {
  title: string;
  data?: TimeSeriesPoint[];
  height?: number;
  stack?: boolean;
  yFormatter?: (value: number) => string;
  tooltipFormatter?: (value: number) => string;
  colorMap?: Record<string, string>;
}

const defaultFormatter = (value: number) => value.toFixed(1);

const AreaPanel: FC<AreaPanelProps> = ({
  title,
  data = [],
  height = 180,
  stack = false,
  yFormatter = defaultFormatter,
  tooltipFormatter = yFormatter,
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
      <Area
        key={chartKey}
        data={data}
        xField="time"
        yField="value"
        seriesField="series"
        stack={stack}
        height={height}
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
        style={{ fillOpacity: 0.3 }}
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

export default AreaPanel;
