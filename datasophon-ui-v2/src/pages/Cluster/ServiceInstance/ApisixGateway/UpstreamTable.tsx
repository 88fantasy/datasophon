import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  ProFormDigit,
  ProFormText,
  ProFormTextArea,
  type ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button } from 'antd';
import React, { useState } from 'react';
import type { ApisixGatewayDoc, ApisixUpstream } from './gatewayYaml';
import { findDuplicateIds, upsertById } from './gatewayYaml';

interface UpstreamTableProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const UpstreamTable: React.FC<UpstreamTableProps> = ({ doc, onChange }) => {
  const { message } = App.useApp();
  const upstreams = doc.upstreams ?? [];
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<ApisixUpstream | null>(null);

  const handleDelete = (record: ApisixUpstream) => {
    onChange({
      ...doc,
      upstreams: upstreams.filter((u) => String(u.id) !== String(record.id)),
    });
  };

  const onFinish = async (values: Record<string, any>) => {
    let nodes: Record<string, number>;
    try {
      nodes = JSON.parse(values.nodesJson || '{}');
    } catch {
      message.error('nodes 不是合法的 JSON');
      return false;
    }
    const updated: ApisixUpstream = {
      ...(editRecord ?? {}),
      id: values.id,
      type: values.type,
      nodes,
    };
    const next = upsertById(upstreams, updated);
    if (findDuplicateIds(next).length > 0) {
      message.error('id 已存在，请使用唯一 id');
      return false;
    }
    onChange({ ...doc, upstreams: next });
    setModalOpen(false);
    return true;
  };

  const columns: ProColumns<ApisixUpstream>[] = [
    { title: 'id', dataIndex: 'id', width: 80 },
    { title: 'type', dataIndex: 'type', width: 120 },
    {
      title: 'nodes',
      dataIndex: 'nodes',
      render: (_, record) => JSON.stringify(record.nodes ?? {}),
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
        <a key="delete" style={{ color: 'red' }} onClick={() => handleDelete(record)}>
          删除
        </a>,
      ],
    },
  ];

  return (
    <>
      <ProTable<ApisixUpstream>
        rowKey="id"
        columns={columns}
        dataSource={upstreams}
        search={false}
        options={false}
        pagination={false}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditRecord(null);
              setModalOpen(true);
            }}
          >
            新建 Upstream
          </Button>,
        ]}
      />
      <ModalForm
        title={editRecord ? '编辑 Upstream' : '新建 Upstream'}
        open={modalOpen}
        onOpenChange={setModalOpen}
        onFinish={onFinish}
        initialValues={
          editRecord
            ? {
                id: editRecord.id,
                type: editRecord.type ?? 'roundrobin',
                nodesJson: JSON.stringify(editRecord.nodes ?? {}, null, 2),
              }
            : { type: 'roundrobin', nodesJson: '{\n  "127.0.0.1:8080": 1\n}' }
        }
        width={480}
        modalProps={{ destroyOnHidden: true }}
      >
        <ProFormDigit
          name="id"
          label="id"
          disabled={!!editRecord}
          rules={[{ required: true, message: '请输入 id' }]}
        />
        <ProFormText name="type" label="type" rules={[{ required: true }]} />
        <ProFormTextArea
          name="nodesJson"
          label="nodes（JSON，host:port -> 权重）"
          fieldProps={{ rows: 6 }}
          rules={[{ required: true, message: '请输入 nodes' }]}
        />
      </ModalForm>
    </>
  );
};

export default UpstreamTable;
