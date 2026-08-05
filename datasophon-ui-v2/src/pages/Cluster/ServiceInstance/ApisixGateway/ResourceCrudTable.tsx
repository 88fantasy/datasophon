/**
 * upstreams/routes/global_rules 三张表共用的增删改脚手架：id 列、操作列、
 * 新建/编辑弹窗、id 唯一性校验、保存回调都在这里统一实现，调用方只需提供
 * 资源特有的列、表单字段与"表单值 -> 记录"的解析函数。
 */

import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  type ProColumns,
  ProFormDigit,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button } from 'antd';
import React, { useState } from 'react';
import { findDuplicateIds, upsertById } from './gatewayYaml';

export interface ResourceCrudTableProps<T extends { id: number | string }> {
  /** 用于按钮/弹窗标题，如「Upstream」「Route」「Global Rule」 */
  resourceLabel: string;
  items: T[];
  onChange: (next: T[]) => void;
  /** 资源特有列（id 列、操作列由本组件统一渲染，这里不用再传） */
  columns: ProColumns<T>[];
  /** 资源特有表单字段（id 字段由本组件统一渲染） */
  formFields: React.ReactNode;
  getInitialValues: (editRecord: T | null) => Record<string, any>;
  /** 把表单值解析为完整记录；返回字符串表示校验失败，字符串内容即错误提示 */
  parseValues: (
    values: Record<string, any>,
    editRecord: T | null,
  ) => T | string;
  /** 删除单条记录后的新列表，默认按 id 过滤 */
  deleteItem?: (items: T[], record: T) => T[];
  /** 某条记录是否允许编辑/删除，默认恒为 true */
  canModify?: (record: T) => boolean;
}

function defaultDeleteItem<T extends { id: number | string }>(
  items: T[],
  record: T,
): T[] {
  return items.filter((item) => String(item.id) !== String(record.id));
}

export function ResourceCrudTable<T extends { id: number | string }>({
  resourceLabel,
  items,
  onChange,
  columns,
  formFields,
  getInitialValues,
  parseValues,
  deleteItem = defaultDeleteItem,
  canModify = () => true,
}: ResourceCrudTableProps<T>) {
  const { message } = App.useApp();
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<T | null>(null);

  const handleDelete = (record: T) => {
    onChange(deleteItem(items, record));
  };

  const onFinish = async (values: Record<string, any>) => {
    const parsed = parseValues(values, editRecord);
    if (typeof parsed === 'string') {
      message.error(parsed);
      return false;
    }
    const next = upsertById(items, parsed);
    if (findDuplicateIds(next).length > 0) {
      message.error('id 已存在，请使用唯一 id');
      return false;
    }
    onChange(next);
    setModalOpen(false);
    return true;
  };

  const allColumns: ProColumns<T>[] = [
    { title: 'id', dataIndex: 'id', width: 80 },
    ...columns,
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) =>
        canModify(record)
          ? [
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
            ]
          : [],
    },
  ];

  return (
    <>
      <ProTable<T>
        rowKey="id"
        columns={allColumns}
        dataSource={items}
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
            新建 {resourceLabel}
          </Button>,
        ]}
      />
      <ModalForm
        title={editRecord ? `编辑 ${resourceLabel}` : `新建 ${resourceLabel}`}
        open={modalOpen}
        onOpenChange={setModalOpen}
        onFinish={onFinish}
        initialValues={getInitialValues(editRecord)}
        width={480}
        modalProps={{ destroyOnHidden: true }}
      >
        <ProFormDigit
          name="id"
          label="id"
          disabled={!!editRecord}
          rules={[{ required: true, message: '请输入 id' }]}
        />
        {formFields}
      </ModalForm>
    </>
  );
}

export default ResourceCrudTable;
