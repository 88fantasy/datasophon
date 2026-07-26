import { type ProColumns, ProTable } from '@ant-design/pro-components';
import { Tag } from 'antd';
import React from 'react';
import { listAlertHistory } from '@/services/alarm';

interface Props {
  clusterId: number;
}

interface HistoryTableParams {
  current?: number;
  pageSize?: number;
  alertTargetName?: string;
  hostname?: string;
  alertLevel?: number;
  status?: number;
  startTime?: string;
  endTime?: string;
}

const LEVEL_META: Record<string, { color: string; label: string }> = {
  warning: { color: 'warning', label: '警告' },
  exception: { color: 'error', label: '异常' },
};

const STATUS_META: Record<string, { color: string; label: string }> = {
  firing: { color: 'error', label: '告警中' },
  resolved: { color: 'success', label: '已恢复' },
};

const HistoryTab: React.FC<Props> = ({ clusterId }) => {
  const columns: ProColumns<DATASOPHON.AlertHistoryResponse>[] = [
    { dataIndex: 'index', title: '序号', valueType: 'indexBorder', width: 48 },
    {
      title: '告警级别',
      dataIndex: 'alertLevel',
      valueType: 'select',
      width: 100,
      valueEnum: {
        1: { text: '警告' },
        2: { text: '异常' },
      },
      render: (_, record) => {
        const meta = LEVEL_META[record.alertLevel];
        return <Tag color={meta?.color}>{meta?.label ?? '-'}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      width: 100,
      valueEnum: {
        1: { text: '告警中' },
        2: { text: '已恢复' },
      },
      render: (_, record) => {
        const meta = STATUS_META[record.status];
        return <Tag color={meta?.color}>{meta?.label ?? '-'}</Tag>;
      },
    },
    {
      title: '告警指标',
      dataIndex: 'alertTargetName',
      ellipsis: true,
      width: 180,
    },
    {
      title: '告警组',
      dataIndex: 'alertGroupName',
      search: false,
      ellipsis: true,
      width: 140,
    },
    {
      title: '主机',
      dataIndex: 'hostname',
      ellipsis: true,
      width: 150,
    },
    {
      title: '告警详情',
      dataIndex: 'alertInfo',
      search: false,
      ellipsis: true,
      width: 220,
    },
    {
      title: '建议操作',
      dataIndex: 'alertAdvice',
      search: false,
      ellipsis: true,
      width: 200,
    },
    {
      title: '告警时间',
      dataIndex: 'createTime',
      search: false,
      width: 170,
    },
    {
      title: '告警时间',
      dataIndex: 'alertTimeRange',
      valueType: 'dateTimeRange',
      hideInTable: true,
      search: {
        transform: (value: string[]) => ({
          startTime: value?.[0],
          endTime: value?.[1],
        }),
      },
    },
  ];

  return (
    <ProTable<DATASOPHON.AlertHistoryResponse, HistoryTableParams>
      rowKey="id"
      locale={{ emptyText: '暂无告警记录' }}
      columns={columns}
      request={async (params) => {
        const response = await listAlertHistory(clusterId, {
          alertTargetName: params.alertTargetName,
          hostname: params.hostname,
          alertLevel: params.alertLevel,
          status: params.status,
          startTime: params.startTime,
          endTime: params.endTime,
          page: params.current ?? 1,
          pageSize: params.pageSize ?? 20,
        });
        const data = response?.data ?? { totalList: [], totalCount: 0 };
        return {
          data: data.totalList ?? [],
          success: true,
          total: data.totalCount ?? 0,
        };
      }}
      search={{ filterType: 'light' }}
      scroll={{ x: 1400 }}
    />
  );
};

export default HistoryTab;
