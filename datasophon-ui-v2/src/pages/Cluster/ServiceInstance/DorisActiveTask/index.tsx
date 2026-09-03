import { useIntl } from '@umijs/max';
import { Spin } from 'antd';
import React from 'react';
import styles from './DorisActiveTask.module.less';

interface DorisActiveTaskProps {
  clusterId: number;
  instanceId: number;
}

const DorisActiveTask: React.FC<DorisActiveTaskProps> = ({
  clusterId,
  instanceId,
}) => {
  const intl = useIntl();
  return (
    <div
      className={styles.panel}
      data-cluster-id={clusterId}
      data-instance-id={instanceId}
    >
      <Spin tip={intl.formatMessage({ id: 'dorisActiveTask.loading' })} />
    </div>
  );
};

export default DorisActiveTask;
