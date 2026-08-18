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

import { Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { FC } from 'react';
import MonitorPanelCard from '../../../monitor/_shared/MonitorPanelCard';

const LEVEL_COLOR: Record<string, string> = {
  exception: 'error',
  warning: 'warning',
};

interface RecentAlertsPanelProps {
  title: string;
  levelLabels: Record<string, string>;
  columnLabels: {
    level: string;
    target: string;
    hostname: string;
    createTime: string;
  };
  emptyText: string;
  viewAllLabel: string;
  data: DATASOPHON.ClusterAlertHistoryRecord[];
  onViewAll: () => void;
  height?: number;
}

const RecentAlertsPanel: FC<RecentAlertsPanelProps> = ({
  title,
  levelLabels,
  columnLabels,
  emptyText,
  viewAllLabel,
  data,
  onViewAll,
  height = 260,
}) => {
  const columns: ColumnsType<DATASOPHON.ClusterAlertHistoryRecord> = [
    {
      title: columnLabels.level,
      dataIndex: 'alertLevel',
      width: 84,
      render: (value: string) => (
        <Tag color={LEVEL_COLOR[value] ?? 'default'}>
          {levelLabels[value] ?? value}
        </Tag>
      ),
    },
    {
      title: columnLabels.target,
      dataIndex: 'alertTargetName',
      ellipsis: true,
    },
    {
      title: columnLabels.hostname,
      dataIndex: 'hostname',
      width: 140,
      ellipsis: true,
    },
    {
      title: columnLabels.createTime,
      dataIndex: 'createTime',
      width: 160,
    },
  ];

  return (
    <MonitorPanelCard
      title={title}
      extra={
        <Typography.Link onClick={onViewAll}>{viewAllLabel}</Typography.Link>
      }
    >
      <Table<DATASOPHON.ClusterAlertHistoryRecord>
        rowKey="id"
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

export default RecentAlertsPanel;
