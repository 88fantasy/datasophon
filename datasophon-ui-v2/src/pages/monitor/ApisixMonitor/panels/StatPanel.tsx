import { Statistic } from 'antd';
import type { FC } from 'react';
import MonitorPanelCard from '../../_shared/MonitorPanelCard';
import useStyles from '../../_shared/monitorStyles';

interface StatPanelProps {
  title: string;
  value: number;
  color?: string;
  suffix?: string;
  precision?: number;
}

const StatPanel: FC<StatPanelProps> = ({
  title,
  value,
  color = '#1677ff',
  suffix,
  precision = 0,
}) => {
  const { styles } = useStyles();

  return (
    <MonitorPanelCard compact>
      <Statistic
        title={<span className={styles.statTitle}>{title}</span>}
        value={value}
        suffix={suffix}
        precision={precision}
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
