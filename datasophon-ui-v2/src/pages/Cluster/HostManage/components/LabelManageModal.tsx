import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ModalForm, ProFormText, ProTable } from '@ant-design/pro-components';
import { Button, Modal, message, Popconfirm } from 'antd';
import React, { useRef, useState } from 'react';
import {
  deleteNodeLabel,
  listNodeLabels,
  saveNodeLabel,
} from '@/services/label';

interface Props {
  clusterId: number;
  trigger: React.ReactElement;
}

const LabelManageModal: React.FC<Props> = ({ clusterId, trigger }) => {
  const [open, setOpen] = useState(false);
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumns<DATASOPHON.NodeLabelResponse>[] = [
    {
      dataIndex: 'index',
      title: '序号',
      valueType: 'indexBorder',
      width: 48,
    },
    {
      title: '标签名称',
      dataIndex: 'nodeLabel',
      ellipsis: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, record) => [
        <Popconfirm
          key="delete"
          title="确认删除该标签？"
          onConfirm={async () => {
            try {
              await deleteNodeLabel(clusterId, record.id);
              message.success('删除成功');
              actionRef.current?.reload();
            } catch {
              // error handled globally
            }
          }}
          okText="确认"
          cancelText="取消"
        >
          <Button type="link" danger size="small">
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <>
      <span onClick={() => setOpen(true)} style={{ display: 'inline-flex' }}>
        {trigger}
      </span>
      <Modal
        title="标签管理"
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={600}
        destroyOnHidden
      >
        <ProTable<DATASOPHON.NodeLabelResponse>
          actionRef={actionRef}
          rowKey="id"
          search={false}
          pagination={false}
          request={async () => {
            const res = await listNodeLabels(clusterId);
            return {
              data: res.data ?? [],
              success: true,
            };
          }}
          columns={columns}
          toolBarRender={() => [
            <ModalForm
              key="add"
              title="新建标签"
              trigger={
                <Button type="primary" icon={<PlusOutlined />}>
                  新建标签
                </Button>
              }
              width={360}
              onFinish={async (values) => {
                try {
                  await saveNodeLabel(clusterId, values.nodeLabel);
                  message.success('标签创建成功');
                  actionRef.current?.reload();
                  return true;
                } catch {
                  return false;
                }
              }}
            >
              <ProFormText
                name="nodeLabel"
                label="标签名称"
                rules={[{ required: true, message: '请输入标签名称' }]}
                placeholder="请输入标签名称"
              />
            </ModalForm>,
          ]}
        />
      </Modal>
    </>
  );
};

export default LabelManageModal;
