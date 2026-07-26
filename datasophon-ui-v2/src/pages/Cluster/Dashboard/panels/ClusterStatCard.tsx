/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons';
import { Statistic } from 'antd';
import type { FC, ReactNode } from 'react';
import MonitorPanelCard from '../../../monitor/_shared/MonitorPanelCard';
import useStyles from '../../../monitor/_shared/monitorStyles';

interface ClusterStatCardProps {
  title: string;
  value: number;
  color: string;
  /** 卡片左侧的彩色图标徽标（参考图每个统计卡左侧都有一个），与 color 同色系 */
  icon: ReactNode;
  /**
   * 较昨日新增/变化的绝对值，可为负；undefined 时不渲染该行。
   *
   * 参考图展示的是"较昨日 +4"这种绝对增量，不是百分比环比——`_shared/panels/StatPanel`
   * 的 `changeRatio` 是为百分比设计的（如 4→400% 这种小基数场景会严重失真），
   * 故本卡片不复用 StatPanel，改用独立的绝对增量展示，视觉样式保持一致。
   */
  delta?: number;
  deltaLabel: string;
}

const ClusterStatCard: FC<ClusterStatCardProps> = ({
  title,
  value,
  color,
  icon,
  delta,
  deltaLabel,
}) => {
  const { styles } = useStyles();
  const noData = !Number.isFinite(value);

  return (
    <MonitorPanelCard compact>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <div
          style={{
            width: 48,
            height: 48,
            flexShrink: 0,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 22,
            color,
            backgroundColor: `${color}1a`,
          }}
        >
          {icon}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Statistic
            title={<span className={styles.statTitle}>{title}</span>}
            value={noData ? '-' : value}
            styles={{
              content: {
                color,
                fontSize: 30,
                fontWeight: 600,
                lineHeight: '38px',
              },
            }}
          />
          {delta !== undefined && (
            <div style={{ fontSize: 12, marginTop: 4 }}>
              {Number.isFinite(delta) ? (
                <span style={{ color: delta >= 0 ? '#52c41a' : '#ff4d4f' }}>
                  {delta >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}{' '}
                  {delta >= 0 ? `+${delta}` : delta}
                </span>
              ) : (
                <span style={{ color: '#8c8c8c' }}>–</span>
              )}
              <span style={{ marginLeft: 4, color: '#8c8c8c' }}>
                {deltaLabel}
              </span>
            </div>
          )}
        </div>
      </div>
    </MonitorPanelCard>
  );
};

export default ClusterStatCard;
