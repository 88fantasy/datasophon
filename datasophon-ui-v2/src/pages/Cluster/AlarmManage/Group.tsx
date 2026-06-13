import { PlusOutlined } from '@ant-design/icons';
import {
  type ActionType,
  type ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button } from 'antd';
import React, { useRef, useState } from 'react';
import { deleteAlertGroups, listAlertGroups } from '@/services/alarm';
import GroupModal from './GroupModal';

interface Props {
  clusterId: number;
  onViewMetrics: (groupId: number) => void;
}

const GroupTab: React.FC<Props> = ({ clusterId, onViewMetrics }) => {
  const { modal, message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);

  const handleDelete = (record: DATASOPHON.AlertGroupResponse) => {
    modal.confirm({
      title: `确认删除告警组「${record.alertGroupName}」？`,
      content: '若已绑定告警指标则无法删除。',
      onOk: async () => {
        await deleteAlertGroups(clusterId, [record.id ?? 0]);
        message.success('删除成功');
        actionRef.current?.reload();
      },
    });
  };

  const columns: ProColumns<DATASOPHON.AlertGroupResponse>[] = [
    { dataIndex: 'index', title: '序号', valueType: 'indexBorder', width: 48 },
    { title: '名称', dataIndex: 'alertGroupName', ellipsis: true },
    {
      title: '模板类别',
      dataIndex: 'alertGroupCategory',
      search: false,
      ellipsis: true,
    },
    {
      title: '告警指标数',
      dataIndex: 'alertQuotaNum',
      search: false,
      ellipsis: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, record) => [
        <a key="view" onClick={() => onViewMetrics(record.id ?? 0)}>
          查看告警指标
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
      <ProTable<DATASOPHON.AlertGroupResponse>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const res = await listAlertGroups(clusterId, {
            alertGroupName: params.alertGroupName,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          });
          const data = res?.data ?? { totalList: [], totalCount: 0 };
          return {
            data: data.totalList ?? [],
            success: true,
            total: data.totalCount ?? 0,
          };
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalOpen(true)}
          >
            新建
          </Button>,
        ]}
        search={{ filterType: 'light' }}
      />
      <GroupModal
        open={modalOpen}
        onOpenChange={setModalOpen}
        clusterId={clusterId}
        onSuccess={() => {
          setModalOpen(false);
          actionRef.current?.reload();
        }}
      />
    </>
  );
};

export default GroupTab;
