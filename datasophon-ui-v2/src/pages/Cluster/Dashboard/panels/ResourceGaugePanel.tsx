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

import { Progress, theme } from 'antd';
import type { FC } from 'react';
import { colorByThreshold } from '../../../monitor/_shared/charts/formatters';
import MonitorPanelCard from '../../../monitor/_shared/MonitorPanelCard';

interface ResourceGaugeItem {
  label: string;
  /** 0-100，非有限值（未取到数）显示为 '-' */
  percent: number;
}

interface ResourceGaugePanelProps {
  title: string;
  items: ResourceGaugeItem[];
}

function formatPercent(value: number) {
  return Number.isFinite(value) ? `${value.toFixed(1)}%` : '-';
}

function clampPercent(value: number) {
  return Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0;
}

/** 横向进度条比仪表环更适合并列比较三个独立的资源使用率。 */
const ResourceGaugePanel: FC<ResourceGaugePanelProps> = ({ title, items }) => {
  const { token } = theme.useToken();

  return (
    <MonitorPanelCard title={title}>
      <div style={{ display: 'grid', gap: 22, padding: '4px 2px 2px' }}>
        {items.map((item) => {
          const percent = clampPercent(item.percent);
          return (
            <div key={item.label}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 8,
                }}
              >
                <span style={{ color: token.colorText }}>{item.label}</span>
                <strong style={{ color: token.colorTextHeading }}>
                  {formatPercent(item.percent)}
                </strong>
              </div>
              <Progress
                percent={percent}
                showInfo={false}
                strokeColor={colorByThreshold(percent, [70, 90])}
                railColor={token.colorFillSecondary}
                size={['100%', 8]}
              />
            </div>
          );
        })}
      </div>
    </MonitorPanelCard>
  );
};

export default ResourceGaugePanel;
