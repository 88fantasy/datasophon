import type { CardProps } from 'antd';
import { Card } from 'antd';
import { clsx } from 'clsx';
import type { CSSProperties, FC, ReactNode } from 'react';
import useStyles from './monitorStyles';

interface MonitorPanelCardProps extends Omit<CardProps, 'styles'> {
  compact?: boolean;
  children: ReactNode;
  styles?: {
    body?: CSSProperties;
    header?: CSSProperties;
    title?: CSSProperties;
  };
}

const MonitorPanelCard: FC<MonitorPanelCardProps> = ({
  compact,
  children,
  className,
  classNames: cardClassNames,
  styles: cardStyles,
  ...rest
}) => {
  const { styles } = useStyles();

  return (
    <Card
      variant="borderless"
      className={clsx(styles.panelCard, className)}
      classNames={{ title: styles.panelCardTitle, ...cardClassNames }}
      styles={{
        body: {
          padding: compact ? 16 : 20,
          ...(cardStyles?.body ?? {}),
        },
        header: {
          minHeight: 48,
          paddingInline: 20,
          borderBottom: 'none',
          ...(cardStyles?.header ?? {}),
        },
        title: {
          fontSize: 14,
          fontWeight: 500,
          ...(cardStyles?.title ?? {}),
        },
        ...cardStyles,
      }}
      {...rest}
    >
      {children}
    </Card>
  );
};

export default MonitorPanelCard;
