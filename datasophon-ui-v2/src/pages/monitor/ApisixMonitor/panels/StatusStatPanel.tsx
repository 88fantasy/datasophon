import { Badge, Statistic } from 'antd';
import type { FC } from 'react';
import MonitorPanelCard from '../../_shared/MonitorPanelCard';
import useStyles from '../../_shared/monitorStyles';
import type { Threshold } from '../utils/formatters';
import { colorByThreshold, labelByThreshold } from '../utils/formatters';

interface StatusStatPanelProps {
  title: string;
  value: number;
  thresholds: Threshold[];
}

const StatusStatPanel: FC<StatusStatPanelProps> = ({
  title,
  value,
  thresholds,
}) => {
  const { styles } = useStyles();
  const color = colorByThreshold(value, thresholds);
  const label = labelByThreshold(value, thresholds);

  return (
    <MonitorPanelCard compact>
      <Statistic
        title={<span className={styles.statTitle}>{title}</span>}
        value={value}
        styles={{
          content: {
            color,
            fontSize: 30,
            fontWeight: 600,
            lineHeight: '38px',
          },
        }}
        suffix={
          label ? (
            <Badge
              color={color}
              text={<span style={{ fontSize: 14, color }}>{label}</span>}
              style={{ marginLeft: 8 }}
            />
          ) : undefined
        }
      />
    </MonitorPanelCard>
  );
};

export default StatusStatPanel;
