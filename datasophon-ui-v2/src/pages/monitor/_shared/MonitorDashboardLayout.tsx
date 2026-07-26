import { GridContent } from '@ant-design/pro-components';
import { Spin, Typography } from 'antd';
import type { FC, ReactNode } from 'react';
import useStyles from './monitorStyles';

const { Text, Title } = Typography;

interface MonitorDashboardLayoutProps {
  title?: ReactNode;
  toolbar?: ReactNode;
  meta?: ReactNode;
  loading?: boolean;
  embedded?: boolean;
  children: ReactNode;
}

const MonitorDashboardLayout: FC<MonitorDashboardLayoutProps> = ({
  title,
  toolbar,
  meta,
  loading,
  embedded = false,
  children,
}) => {
  const { styles, cx } = useStyles();

  return (
    <GridContent
      className={cx(styles.dashboard, embedded && styles.embeddedDashboard)}
    >
      <div className={styles.header}>
        {title && (
          <Title level={4} className={styles.title}>
            {title}
          </Title>
        )}
        {toolbar}
        {(meta || loading) && (
          <Text type="secondary" className={styles.meta}>
            {meta}
            {loading && <Spin size="small" className={styles.metaSpin} />}
          </Text>
        )}
      </div>
      <div className={styles.content}>{children}</div>
    </GridContent>
  );
};

export default MonitorDashboardLayout;
