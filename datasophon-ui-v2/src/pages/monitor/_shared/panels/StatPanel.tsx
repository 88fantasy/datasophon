import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons';
import { Statistic } from 'antd';
import type { FC } from 'react';
import MonitorPanelCard from '../MonitorPanelCard';
import useStyles from '../monitorStyles';

interface StatPanelProps {
  title: string;
  value: number;
  color?: string;
  suffix?: string;
  precision?: number;
  formatter?: (value: number) => string;
  /** (current-previous)/previous，用于展示环比；未传时不渲染环比行 */
  changeRatio?: number;
  changeLabel?: string;
}

const StatPanel: FC<StatPanelProps> = ({
  title,
  value,
  color = '#1677ff',
  suffix,
  precision = 0,
  formatter,
  changeRatio,
  changeLabel = '日环比',
}) => {
  const { styles } = useStyles();

  // 非有限值（NaN/Infinity，通常来自缺失 series 或 0/0）统一显示为 '–'，
  // 而非把 NaN 透传给 formatter / Statistic 渲染出 'NaN'。
  const noData = !Number.isFinite(value);

  return (
    <MonitorPanelCard compact>
      <Statistic
        title={<span className={styles.statTitle}>{title}</span>}
        value={noData ? '-' : value}
        suffix={noData ? undefined : suffix}
        precision={noData || formatter ? undefined : precision}
        formatter={!noData && formatter ? () => formatter(value) : undefined}
        styles={{
          content: {
            color,
            fontSize: 30,
            fontWeight: 600,
            lineHeight: '38px',
          },
        }}
      />
      {changeRatio !== undefined && (
        <div style={{ fontSize: 12, marginTop: 4 }}>
          {Number.isFinite(changeRatio) ? (
            <span style={{ color: changeRatio >= 0 ? '#52c41a' : '#ff4d4f' }}>
              {changeRatio >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}{' '}
              {Math.abs(changeRatio * 100).toFixed(1)}%
            </span>
          ) : (
            <span style={{ color: '#8c8c8c' }}>–</span>
          )}
          <span style={{ marginLeft: 4, color: '#8c8c8c' }}>{changeLabel}</span>
        </div>
      )}
    </MonitorPanelCard>
  );
};

export default StatPanel;
