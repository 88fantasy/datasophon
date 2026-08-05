import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  ProFormDigit,
  ProFormTextArea,
  type ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Tag } from 'antd';
import React, { useState } from 'react';
import { useIntl } from '@umijs/max';
import {
  findDuplicateIds,
  isBuiltinGlobalRule,
  removeGlobalRule,
  upsertById,
  type ApisixGatewayDoc,
  type ApisixGlobalRule,
} from './gatewayYaml';

interface GlobalRuleTableProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const GlobalRuleTable: React.FC<GlobalRuleTableProps> = ({ doc, onChange }) => {
  const intl = useIntl();
  const { message } = App.useApp();
  const rules = doc.global_rules ?? [];
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<ApisixGlobalRule | null>(null);

  const handleDelete = (record: ApisixGlobalRule) => {
    onChange({ ...doc, global_rules: removeGlobalRule(rules, record.id) });
  };

  const onFinish = async (values: Record<string, any>) => {
    let plugins: Record<string, unknown>;
    try {
      plugins = JSON.parse(values.pluginsJson || '{}');
    } catch {
      message.error('plugins 不是合法的 JSON');
      return false;
    }
    const updated: ApisixGlobalRule = { ...(editRecord ?? {}), id: values.id, plugins };
    const next = upsertById(rules, updated);
    if (findDuplicateIds(next).length > 0) {
      message.error('id 已存在，请使用唯一 id');
      return false;
    }
    onChange({ ...doc, global_rules: next });
    setModalOpen(false);
    return true;
  };

  const columns: ProColumns<ApisixGlobalRule>[] = [
    { title: 'id', dataIndex: 'id', width: 80 },
    {
      title: 'plugins',
      dataIndex: 'plugins',
      render: (_, record) =>
        record.plugins ? Object.keys(record.plugins).join(', ') : '-',
    },
    {
      title: '',
      dataIndex: 'builtin',
      width: 100,
      render: (_, record) =>
        isBuiltinGlobalRule(record) ? (
          <Tag color="blue">
            {intl.formatMessage({ id: 'pages.apisixGateway.globalRule.builtin' })}
          </Tag>
        ) : null,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) =>
        isBuiltinGlobalRule(record)
          ? []
          : [
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
      <ProTable<ApisixGlobalRule>
        rowKey="id"
        columns={columns}
        dataSource={rules}
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
            新建 Global Rule
          </Button>,
        ]}
      />
      <ModalForm
        title={editRecord ? '编辑 Global Rule' : '新建 Global Rule'}
        open={modalOpen}
        onOpenChange={setModalOpen}
        onFinish={onFinish}
        initialValues={
          editRecord
            ? {
                id: editRecord.id,
                pluginsJson: JSON.stringify(editRecord.plugins ?? {}, null, 2),
              }
            : { pluginsJson: '{\n\n}' }
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
        <ProFormTextArea
          name="pluginsJson"
          label="plugins（JSON）"
          fieldProps={{ rows: 6 }}
          rules={[{ required: true, message: '请输入 plugins' }]}
        />
      </ModalForm>
    </>
  );
};

export default GlobalRuleTable;
