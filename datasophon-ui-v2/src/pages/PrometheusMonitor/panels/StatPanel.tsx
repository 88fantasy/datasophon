import { Card, Statistic } from 'antd';
import type { FC } from 'react';

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
}) => (
  <Card variant="borderless" style={{ height: '100%' }}>
    <Statistic
      title={title}
      value={value}
      suffix={suffix}
      precision={formatter ? undefined : precision}
      formatter={formatter ? () => formatter(value) : undefined}
      styles={{ content: { color, fontSize: 32, fontWeight: 600 } }}
    />
  </Card>
);

export default StatPanel;
