import { Badge, Card, Statistic } from 'antd';
import type { FC } from 'react';
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
  const color = colorByThreshold(value, thresholds);
  const label = labelByThreshold(value, thresholds);

  return (
    <Card variant="borderless" style={{ height: '100%' }}>
      <Statistic
        title={title}
        value={value}
        valueStyle={{ color, fontSize: 28, fontWeight: 600 }}
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
    </Card>
  );
};

export default StatusStatPanel;
