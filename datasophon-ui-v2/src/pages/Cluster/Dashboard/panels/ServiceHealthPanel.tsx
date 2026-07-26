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

import { Progress, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { FC } from 'react';
import { colorByThreshold } from '../../../monitor/_shared/charts/formatters';
import MonitorPanelCard from '../../../monitor/_shared/MonitorPanelCard';

/** 后端 ServiceState 枚举的中文 desc → Tag 颜色 */
const STATE_COLOR: Record<string, string> = {
  正常: 'success',
  存在告警: 'warning',
  存在异常: 'error',
  待安装: 'default',
};

interface ServiceHealthPanelProps {
  title: string;
  columnLabels: {
    service: string;
    roles: string;
    health: string;
    alertNum: string;
    state: string;
  };
  emptyText: string;
  viewMoreLabel: string;
  data: DATASOPHON.ClusterDashboardServiceHealth[];
  onViewMore: () => void;
  height?: number;
}

const ServiceHealthPanel: FC<ServiceHealthPanelProps> = ({
  title,
  columnLabels,
  emptyText,
  viewMoreLabel,
  data,
  onViewMore,
  height = 260,
}) => {
  const columns: ColumnsType<DATASOPHON.ClusterDashboardServiceHealth> = [
    {
      title: columnLabels.service,
      dataIndex: 'label',
      ellipsis: true,
      render: (label: string, row) => label || row.serviceName,
    },
    {
      title: columnLabels.roles,
      width: 110,
      render: (_, row) => `${row.runningRoles} / ${row.totalRoles}`,
    },
    {
      title: columnLabels.health,
      dataIndex: 'healthPercent',
      width: 140,
      render: (value: number | null) =>
        value === null ? (
          '-'
        ) : (
          <Progress
            percent={value}
            size="small"
            format={(percent) => `${(percent ?? 0).toFixed(0)}%`}
            strokeColor={colorByThreshold(value, [70, 90], { reverse: true })}
          />
        ),
    },
    {
      title: columnLabels.alertNum,
      dataIndex: 'alertNum',
      width: 76,
    },
    {
      title: columnLabels.state,
      dataIndex: 'serviceState',
      width: 92,
      render: (value: string) => (
        <Tag color={STATE_COLOR[value] ?? 'default'}>{value}</Tag>
      ),
    },
  ];

  return (
    <MonitorPanelCard
      title={title}
      extra={<Typography.Link onClick={onViewMore}>{viewMoreLabel}</Typography.Link>}
    >
      <Table<DATASOPHON.ClusterDashboardServiceHealth>
        rowKey="serviceName"
        columns={columns}
        dataSource={data}
        pagination={false}
        size="small"
        scroll={{ y: height - 48 }}
        locale={{ emptyText }}
      />
    </MonitorPanelCard>
  );
};

export default ServiceHealthPanel;
