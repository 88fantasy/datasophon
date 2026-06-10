import { PlusOutlined } from '@ant-design/icons';
import {
  type ActionType,
  type ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Empty, Spin } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import {
  deleteYarnQueues,
  getYarnSchedulerInfo,
  listYarnQueues,
  refreshYarnQueues,
} from '@/services/datasophon/service';
import BuildOrEditModal from './BuildOrEditModal';

interface Props {
  clusterId: number;
}

const QueueTab: React.FC<Props> = ({ clusterId }) => {
  const { modal, message } = App.useApp();
  const actionRef = useRef<ActionType>();

  const [schedulerType, setSchedulerType] = useState<string | null>(null);
  const [schedulerLoading, setSchedulerLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<DATASOPHON.YarnQueue | null>(
    null,
  );

  useEffect(() => {
    getYarnSchedulerInfo(clusterId)
      .then((res) => {
        const type = (res as any)?.data ?? res;
        setSchedulerType(typeof type === 'string' ? type : null);
      })
      .catch(() => setSchedulerType(null))
      .finally(() => setSchedulerLoading(false));
  }, [clusterId]);

  const handleDelete = (record: DATASOPHON.YarnQueue) => {
    modal.confirm({
      title: `确认删除队列「${record.queueName}」？`,
      onOk: async () => {
        await deleteYarnQueues(clusterId, [record.id ?? 0]);
        message.success('删除成功');
        actionRef.current?.reload();
      },
    });
  };

  const handleRefresh = async () => {
    try {
      await refreshYarnQueues(clusterId);
      message.success('刷新成功');
      actionRef.current?.reload();
    } catch {
      message.error('刷新失败');
    }
  };

  const columns: ProColumns<DATASOPHON.YarnQueue>[] = [
    {
      dataIndex: 'index',
      title: '序号',
      valueType: 'indexBorder',
      width: 48,
    },
    {
      title: '队列名称',
      dataIndex: 'queueName',
      ellipsis: true,
    },
    {
      title: '最小资源数',
      dataIndex: 'minCore',
      search: false,
      ellipsis: true,
      render: (_, record) => `${record.minCore}Core, ${record.minMem}GB`,
    },
    {
      title: '最大资源数',
      dataIndex: 'maxCore',
      search: false,
      ellipsis: true,
      render: (_, record) => `${record.maxCore}Core, ${record.maxMem}GB`,
    },
    {
      title: '资源分配策略',
      dataIndex: 'schedulePolicy',
      search: false,
      ellipsis: true,
    },
    {
      title: '权重',
      dataIndex: 'weight',
      search: false,
      ellipsis: true,
    },
    {
      title: '是否允许资源被抢占',
      dataIndex: 'allowPreemption',
      search: false,
      ellipsis: true,
      render: (_, record) => (record.allowPreemption === 1 ? '是' : '否'),
    },
    {
      title: 'AM 占用比例',
      dataIndex: 'amShare',
      search: false,
      ellipsis: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
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

  if (schedulerLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  // Capacity 调度器：未实现，与旧版保持一致
  if (schedulerType === 'capacity') {
    return <Empty description="Capacity 调度器队列管理暂不支持" />;
  }

  return (
    <>
      <ProTable<DATASOPHON.YarnQueue>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          const res = await listYarnQueues(clusterId, {
            page: current,
            pageSize,
          });
          const data = Array.isArray(res) ? (res as any) : (res as any).data;
          return {
            data: data?.data ?? [],
            success: true,
            total: data?.total ?? 0,
          };
        }}
        search={false}
        options={false}
        pagination={{ pageSize: 20 }}
        toolBarRender={() => [
          <Button key="refresh" onClick={handleRefresh}>
            刷新队列
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
      />
      <BuildOrEditModal
        open={modalOpen}
        onOpenChange={setModalOpen}
        record={editRecord}
        clusterId={clusterId}
        onSuccess={() => {
          setModalOpen(false);
          actionRef.current?.reload();
        }}
      />
    </>
  );
};

export default QueueTab;
