import { Card, Statistic } from 'antd';
import type { FC } from 'react';

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
}) => (
  <Card variant="borderless" style={{ height: '100%' }}>
    <Statistic
      title={title}
      value={value}
      suffix={suffix}
      precision={precision}
      valueStyle={{ color, fontSize: 28, fontWeight: 600 }}
    />
  </Card>
);

export default StatPanel;
