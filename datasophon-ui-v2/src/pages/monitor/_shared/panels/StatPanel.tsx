import { Statistic } from 'antd';
import type { FC } from 'react';
import MonitorPanelCard from '../MonitorPanelCard';
import useStyles from '../monitorStyles';

interface StatPanelProps {
  title: string;
  value: number;
  color?: string;
  suffix?: string;
  precision?: number;
  formatter?: (value: number) => string;
}

const StatPanel: FC<StatPanelProps> = ({
  title,
  value,
  color = '#1677ff',
  suffix,
  precision = 0,
  formatter,
}) => {
  const { styles } = useStyles();

  // 非有限值（NaN/Infinity，通常来自缺失 series 或 0/0）统一显示为 '–'，
  // 而非把 NaN 透传给 formatter / Statistic 渲染出 'NaN'。
  const noData = !Number.isFinite(value);

  return (
    <MonitorPanelCard compact>
      <Statistic
        title={<span className={styles.statTitle}>{title}</span>}
        value={noData ? '-' : value}
        suffix={noData ? undefined : suffix}
        precision={noData || formatter ? undefined : precision}
        formatter={!noData && formatter ? () => formatter(value) : undefined}
        styles={{
          content: {
            color,
            fontSize: 30,
            fontWeight: 600,
            lineHeight: '38px',
          },
        }}
      />
    </MonitorPanelCard>
  );
};

export default StatPanel;
