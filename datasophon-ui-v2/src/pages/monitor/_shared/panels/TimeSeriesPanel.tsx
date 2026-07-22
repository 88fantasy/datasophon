import { Area, Line } from '@ant-design/plots';
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

// mergeNamedSeries 对 groupBy 产生的多序列会拼成 "label (值)"（见 charts/promql.ts），
// colorMap 仍按原始 label 声明（如 DorisMonitor DO-C03 的 local_used_pct/avail_pct）；
// 精确匹配失败时退回剥离 " (值)" 后缀的 base label，使既有 colorMap 无需跟着改。
export function baseSeriesLabel(name: string): string {
  const idx = name.indexOf(' (');
  return idx === -1 ? name : name.slice(0, idx);
}

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
      colorMap?.[baseSeriesLabel(name)] ??
      CHART_COLORS.series[index % CHART_COLORS.series.length],
  );

  const chartKey =
    data.length > 0 ? `${data[0].time}-${data[data.length - 1].time}` : 'empty';

  // 单序列图对齐设计稿的柔和渐变面积填充；多序列图（如 p50/p75/p99 分位数）
  // 保持纯线条，避免半透明填充相互重叠把颜色叠脏。
  const isSingleSeries = seriesNames.length === 1;
  const ChartComponent = isSingleSeries ? Area : Line;

  return (
    <MonitorPanelCard title={title}>
      <ChartComponent
        key={chartKey}
        data={data}
        xField="time"
        yField="value"
        seriesField="series"
        height={height}
        smooth
        style={isSingleSeries ? { fillOpacity: 0.3 } : undefined}
        axis={{
          x: {
            labelFormatter: (value: number) => dayjs(value).format('HH:mm'),
            tickCount: 5,
            grid: false,
          },
          y: { labelFormatter: yFormatter, grid: false },
        }}
        scale={{
          x: { type: 'time' },
          color: { type: 'ordinal', range: colorRange },
        }}
        legend={{ position: 'top-right' }}
        annotations={thresholdLines?.map((line) => ({
          type: 'lineY',
          data: [line.value],
          style: {
            stroke: line.color ?? CHART_COLORS.warning,
            lineDash: [4, 4],
          },
          ...(line.label
            ? {
                labels: [
                  {
                    text: line.label,
                    fill: line.color ?? CHART_COLORS.warning,
                    fontSize: 10,
                    position: 'right',
                  },
                ],
              }
            : {}),
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
