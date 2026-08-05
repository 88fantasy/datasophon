import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  type ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button } from 'antd';
import React, { useState } from 'react';
import type { ApisixGatewayDoc, ApisixRoute } from './gatewayYaml';
import { findDuplicateIds, upsertById } from './gatewayYaml';

interface RouteTableProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const RouteTable: React.FC<RouteTableProps> = ({ doc, onChange }) => {
  const { message } = App.useApp();
  const routes = doc.routes ?? [];
  const upstreamOptions = (doc.upstreams ?? []).map((u) => ({
    label: String(u.id),
    value: u.id,
  }));
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<ApisixRoute | null>(null);

  const handleDelete = (record: ApisixRoute) => {
    onChange({
      ...doc,
      routes: routes.filter((r) => String(r.id) !== String(record.id)),
    });
  };

  const onFinish = async (values: Record<string, any>) => {
    let plugins: Record<string, unknown> | undefined;
    if (values.pluginsJson?.trim()) {
      try {
        plugins = JSON.parse(values.pluginsJson);
      } catch {
        message.error('plugins 不是合法的 JSON');
        return false;
      }
    }
    const updated: ApisixRoute = {
      ...(editRecord ?? {}),
      id: values.id,
      uri: values.uri,
      upstream_id: values.upstream_id,
      plugins,
    };
    const next = upsertById(routes, updated);
    if (findDuplicateIds(next).length > 0) {
      message.error('id 已存在，请使用唯一 id');
      return false;
    }
    onChange({ ...doc, routes: next });
    setModalOpen(false);
    return true;
  };

  const columns: ProColumns<ApisixRoute>[] = [
    { title: 'id', dataIndex: 'id', width: 80 },
    { title: 'uri', dataIndex: 'uri' },
    { title: 'upstream_id', dataIndex: 'upstream_id', width: 120 },
    {
      title: 'plugins',
      dataIndex: 'plugins',
      render: (_, record) =>
        record.plugins ? Object.keys(record.plugins).join(', ') : '-',
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
      <ProTable<ApisixRoute>
        rowKey="id"
        columns={columns}
        dataSource={routes}
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
            新建 Route
          </Button>,
        ]}
      />
      <ModalForm
        title={editRecord ? '编辑 Route' : '新建 Route'}
        open={modalOpen}
        onOpenChange={setModalOpen}
        onFinish={onFinish}
        initialValues={
          editRecord
            ? {
                id: editRecord.id,
                uri: editRecord.uri,
                upstream_id: editRecord.upstream_id,
                pluginsJson: editRecord.plugins
                  ? JSON.stringify(editRecord.plugins, null, 2)
                  : '',
              }
            : {}
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
        <ProFormText name="uri" label="uri" rules={[{ required: true }]} />
        <ProFormSelect
          name="upstream_id"
          label="upstream_id"
          options={upstreamOptions}
          rules={[{ required: true, message: '请选择 upstream' }]}
        />
        <ProFormTextArea
          name="pluginsJson"
          label="plugins（JSON，可留空）"
          fieldProps={{ rows: 6 }}
        />
      </ModalForm>
    </>
  );
};

export default RouteTable;
