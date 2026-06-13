import { PlusOutlined } from '@ant-design/icons';
import {
  type ActionType,
  type ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Tag } from 'antd';
import React, { useRef, useState } from 'react';
import {
  deleteAlertQuotas,
  listAlertGroups,
  listAlertQuotas,
  startQuotas,
  stopQuotas,
} from '@/services/datasophon/alarm';
import MetricModal from './MetricModal';

interface Props {
  clusterId: number;
  defaultGroupId?: number;
}

const MetricTab: React.FC<Props> = ({ clusterId, defaultGroupId }) => {
  const { modal, message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<DATASOPHON.AlertQuota | null>(
    null,
  );
  const [selectedRows, setSelectedRows] = useState<DATASOPHON.AlertQuota[]>([]);

  const handleDelete = (record: DATASOPHON.AlertQuota) => {
    modal.confirm({
      title: `确认删除指标「${record.alertQuotaName}」？`,
      onOk: async () => {
        await deleteAlertQuotas(clusterId, [record.id ?? 0]);
        message.success('删除成功');
        actionRef.current?.reload();
      },
    });
  };

  const handleBatchAction = async (type: 'start' | 'stop') => {
    if (!selectedRows.length) {
      message.warning('请选择要操作的项');
      return;
    }
    const ids = selectedRows.map((r) => r.id ?? 0).join(',');
    if (type === 'start') {
      await startQuotas(clusterId, ids);
    } else {
      await stopQuotas(clusterId, ids);
    }
    message.success('操作成功');
    setSelectedRows([]);
    actionRef.current?.reload();
  };

  const STATUS_COLOR: Record<number, string> = { 1: 'success', 2: 'error' };

  const columns: ProColumns<DATASOPHON.AlertQuota>[] = [
    { dataIndex: 'index', title: '序号', valueType: 'indexBorder', width: 48 },
    { title: '指标名称', dataIndex: 'alertQuotaName', ellipsis: true },
    {
      title: '比较方式',
      dataIndex: 'compareMethod',
      search: false,
      ellipsis: true,
    },
    {
      title: '告警阀值',
      dataIndex: 'alertThreshold',
      search: false,
      ellipsis: true,
    },
    {
      title: '告警组',
      dataIndex: 'alertGroupId',
      ellipsis: true,
      valueType: 'select',
      request: async () => {
        const res = await listAlertGroups(clusterId, {
          page: 1,
          pageSize: 1000,
        });
        const list = (res as any)?.data?.totalList ?? [];
        return Array.isArray(list)
          ? list.map((g: DATASOPHON.AlertGroup) => ({
              label: g.alertGroupName,
              value: g.id,
            }))
          : [];
      },
      render: (_, record) => record.alertGroupName ?? record.alertGroupId,
    },
    {
      title: '通知组',
      dataIndex: 'noticeGroupId',
      search: false,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'quotaStateCode',
      search: false,
      render: (_, record) => (
        <Tag color={STATUS_COLOR[record.quotaStateCode ?? 0] ?? 'default'}>
          {record.quotaState ?? '-'}
        </Tag>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) => [
        <a
          key="edit"
          onClick={() => {
            setEditRecord(record);
            setModalOpen(true);
          }}
        >
          编辑
        </a>,
        <a
          key="delete"
          style={{ color: 'red' }}
          onClick={() => handleDelete(record)}
        >
          删除
        </a>,
      ],
    },
  ];

  return (
    <>
      <ProTable<DATASOPHON.AlertQuota>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        params={{ defaultGroupId }}
        request={async (params) => {
          const res = await listAlertQuotas(clusterId, {
            alertGroupId: params.defaultGroupId || params.alertGroupId,
            quotaName: params.alertQuotaName,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          });
          const data = (res as any)?.data ?? {};
          return {
            data: data.totalList ?? [],
            success: true,
            total: data.totalCount ?? 0,
          };
        }}
        rowSelection={{
          selectedRowKeys: selectedRows.map((r) => r.id ?? 0),
          onChange: (_, rows) => setSelectedRows(rows),
        }}
        toolBarRender={() => [
          <Button key="start" onClick={() => handleBatchAction('start')}>
            启用
          </Button>,
          <Button key="stop" onClick={() => handleBatchAction('stop')}>
            停用
          </Button>,
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditRecord(null);
              setModalOpen(true);
            }}
          >
            新建
          </Button>,
        ]}
        search={{ filterType: 'light' }}
      />
      <MetricModal
        open={modalOpen}
        onOpenChange={(v) => {
          setModalOpen(v);
          if (!v) setEditRecord(null);
        }}
        record={editRecord}
        clusterId={clusterId}
        defaultGroupId={defaultGroupId}
        onSuccess={() => {
          setModalOpen(false);
          setEditRecord(null);
          actionRef.current?.reload();
        }}
      />
    </>
  );
};

export default MetricTab;
