import { Card, Statistic } from 'antd';
import type { ReactNode } from 'react';

import { useObservabilityStyles } from './observabilityStyles';

export interface OverviewStat {
  title: ReactNode;
  value: string | number;
  hint?: ReactNode;
  tone?: 'default' | 'success' | 'warning' | 'danger';
}

interface OverviewStatsProps {
  items: OverviewStat[];
}

const toneColors: Record<
  NonNullable<OverviewStat['tone']>,
  string | undefined
> = {
  default: undefined,
  success: '#389e0d',
  warning: '#d48806',
  danger: '#cf1322',
};

const OverviewStats: React.FC<OverviewStatsProps> = ({ items }) => {
  const { styles } = useObservabilityStyles();

  return (
    <div className={styles.overviewGrid}>
      {items.map((item) => (
        <Card
          key={String(item.title)}
          size="small"
          className={styles.overviewCard}
        >
          <Statistic
            title={item.title}
            value={item.value}
            styles={{ content: { color: toneColors[item.tone ?? 'default'] } }}
          />
          {item.hint && <div className={styles.statHint}>{item.hint}</div>}
        </Card>
      ))}
    </div>
  );
};

export default OverviewStats;
