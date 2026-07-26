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

import { Progress } from 'antd';
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

/**
 * CPU/内存/磁盘三个独立仪表环。
 *
 * 参考图的「资源使用占比」环形图把三者拼成一个饼图，但饼图的弧长天然表示
 * “占总和的比例”，CPU/内存/磁盘三个互不相干的使用率强行拼一起会被饼图
 * 自动归一化（如 2%+35%+8%=45 会被拉伸显示成约 4.4%/78%/18%），数值失真。
 * 改成三个独立的 dashboard 环，每个环各自 0-100% 展示，语义正确——
 * 与 K8sDashboard（同仓库 `Cluster/K8sDashboard/index.tsx`）的 capacity 环用法一致。
 */
const ResourceGaugePanel: FC<ResourceGaugePanelProps> = ({ title, items }) => (
  <MonitorPanelCard title={title}>
    <div style={{ display: 'flex', justifyContent: 'space-around', flexWrap: 'wrap' }}>
      {items.map((item) => (
        <div key={item.label} style={{ textAlign: 'center', padding: '0 8px' }}>
          <Progress
            type="dashboard"
            size={92}
            percent={clampPercent(item.percent)}
            format={() => formatPercent(item.percent)}
            strokeColor={colorByThreshold(clampPercent(item.percent), [70, 90])}
          />
          <div style={{ marginTop: 8, color: '#8c8c8c', fontSize: 13 }}>
            {item.label}
          </div>
        </div>
      ))}
    </div>
  </MonitorPanelCard>
);

export default ResourceGaugePanel;
