import { Area } from '@ant-design/plots';
import { Empty } from 'antd';
import dayjs from 'dayjs';
import type { FC } from 'react';
import MonitorPanelCard from '../../_shared/MonitorPanelCard';
import useStyles from '../../_shared/monitorStyles';
import type { TimeSeriesPoint } from '../mock/apisixMockData';
import { formatBytes } from '../utils/formatters';

interface AreaPanelProps {
  title: string;
  data: TimeSeriesPoint[];
  height?: number;
  stack?: boolean;
  unit?: 'bytes' | 'percent' | 'short';
  colorMap?: Record<string, string>;
}

const AreaPanel: FC<AreaPanelProps> = ({
  title,
  data,
  height = 180,
  stack = false,
  unit = 'short',
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

  const yFormatter = (v: number) => {
    if (unit === 'bytes') return `${formatBytes(v)}/s`;
    if (unit === 'percent') return `${v.toFixed(0)}%`;
    return v.toFixed(1);
  };

  return (
    <MonitorPanelCard title={title}>
      <Area
        data={data}
        xField="time"
        yField="value"
        seriesField="series"
        stack={stack}
        height={height}
        axis={{
          x: {
            labelFormatter: (v: number) => dayjs(v).format('HH:mm'),
            tickCount: 5,
          },
          y: { labelFormatter: yFormatter },
        }}
        scale={{
          x: { type: 'time' },
          ...(colorRange
            ? { color: { type: 'ordinal', range: colorRange } }
            : {}),
        }}
        legend={{ position: 'top-right' }}
        style={{ fillOpacity: 0.3 }}
        tooltip={{
          title: (d: TimeSeriesPoint) => dayjs(d.time).format('HH:mm:ss'),
          items: [
            (d: TimeSeriesPoint) => ({
              name: d.series,
              value: yFormatter(d.value),
            }),
          ],
        }}
      />
    </MonitorPanelCard>
  );
};
export default AreaPanel;
