import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { Collapse, Typography } from 'antd';
import React from 'react';

import type { ToolCallInfo } from './data';
import { useStyles } from './style';

interface Props {
  items: ToolCallInfo[];
}

const ToolCalls: React.FC<Props> = ({ items }) => {
  const { styles } = useStyles();

  const collapseItems = items.map((tc, idx) => ({
    key: idx,
    label: (
      <span className={styles.toolCallHeader}>
        <ToolOutlined />
        <span className={styles.toolCallName}>{tc.name}</span>
        {tc.isError ? (
          <CloseCircleOutlined className={styles.toolCallError} />
        ) : (
          <CheckCircleOutlined className={styles.toolCallSuccess} />
        )}
        <span className={styles.toolCallDuration}>{tc.durationMs}ms</span>
      </span>
    ),
    children: (
      <div className={styles.toolCallBody}>
        <Typography.Text type="secondary" className={styles.toolCallLabel}>
          参数
        </Typography.Text>
        <pre className={styles.toolCallPre}>
          {JSON.stringify(tc.args, null, 2)}
        </pre>
        <Typography.Text type="secondary" className={styles.toolCallLabel}>
          结果
        </Typography.Text>
        <pre className={styles.toolCallPre}>{tc.result}</pre>
      </div>
    ),
  }));

  return (
    <Collapse
      ghost
      size="small"
      items={collapseItems}
      className={styles.toolCallsPanel}
    />
  );
};

export default ToolCalls;
