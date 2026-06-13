import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Empty, Modal, message, Popconfirm, Segmented, Space, Spin, Tabs } from 'antd';
import hljs from 'highlight.js/lib/core';
import json from 'highlight.js/lib/languages/json';
import 'highlight.js/styles/github.min.css';
import React, { useState } from 'react';
import Editor from 'react-simple-code-editor';
import {
  deleteFrameK8sService,
  deleteFrameService,
  getFrameServiceDdl,
  listFrameServices,
  updateFrameServiceDdl,
} from '@/services/frame';

hljs.registerLanguage('json', json);

type ClusterType = 'physical' | 'k8s';

interface DdlModalState {
  open: boolean;
  serviceId: number | null;
  serviceName: string;
  content: string;
  loading: boolean;
  saving: boolean;
}

const INITIAL_DDL_MODAL: DdlModalState = {
  open: false,
  serviceId: null,
  serviceName: '',
  content: '',
  loading: false,
  saving: false,
};

const FrameManage: React.FC = () => {
  const { data: frames = [], refresh, loading } = useRequest(listFrameServices, {
    formatResult: (res: any) => (res?.data ?? []) as DATASOPHON.FrameWithServices[],
  });
  const [ddlModal, setDdlModal] = useState<DdlModalState>(INITIAL_DDL_MODAL);

  // ── DDL 编辑 ──────────────────────────────────────────────────────────

  const handleOpenDdl = async (record: DATASOPHON.FrameServiceItem) => {
    setDdlModal({
      open: true,
      serviceId: record.id,
      serviceName: record.serviceName,
      content: '',
      loading: true,
      saving: false,
    });
    try {
      const result = await getFrameServiceDdl(record.id);
      setDdlModal((prev) => ({
        ...prev,
        content: result?.data ?? '',
        loading: false,
      }));
    } catch {
      setDdlModal((prev) => ({ ...prev, loading: false }));
    }
  };

  const handleSaveDdl = async () => {
    if (!ddlModal.serviceId) return;
    try {
      JSON.parse(ddlModal.content);
    } catch {
      message.error('JSON 格式错误，请检查后重试');
      return;
    }
    setDdlModal((prev) => ({ ...prev, saving: true }));
    try {
      await updateFrameServiceDdl(ddlModal.serviceId, ddlModal.content);
      message.success('DDL 已更新');
      setDdlModal(INITIAL_DDL_MODAL);
    } catch {
      setDdlModal((prev) => ({ ...prev, saving: false }));
    }
  };

  // ── 列定义 ────────────────────────────────────────────────────────────

  const physicalColumns: ProColumns<DATASOPHON.FrameServiceItem>[] = [
    { dataIndex: 'index', title: '序号', valueType: 'indexBorder', width: 48 },
    { title: '服务', dataIndex: 'serviceName', ellipsis: true },
    { title: '版本', dataIndex: 'serviceVersion', ellipsis: true, width: 120 },
    { title: '描述', dataIndex: 'serviceDesc', ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => (
        <Space>
          <Popconfirm
            title={`确认删除服务「${record.serviceName}」?`}
            onConfirm={async () => {
              await deleteFrameService(record.id);
              message.success('删除成功');
              refresh();
            }}
          >
            <a>删除</a>
          </Popconfirm>
          <a onClick={() => handleOpenDdl(record)}>编辑</a>
        </Space>
      ),
    },
  ];

  const k8sColumns: ProColumns<DATASOPHON.FrameK8sServiceItem>[] = [
    { dataIndex: 'index', title: '序号', valueType: 'indexBorder', width: 48 },
    { title: '服务', dataIndex: 'serviceName', ellipsis: true },
    { title: '版本', dataIndex: 'serviceVersion', ellipsis: true, width: 120 },
    { title: '描述', dataIndex: 'serviceDesc', ellipsis: true },
    {
      title: '支持的制品类型',
      dataIndex: 'supportArtifacts',
      ellipsis: true,
      render: (_, record) =>
        Array.isArray(record.supportArtifacts)
          ? record.supportArtifacts.join(', ')
          : '-',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, record) => (
        <Popconfirm
          title={`确认删除服务「${record.serviceName}」?`}
          onConfirm={async () => {
            await deleteFrameK8sService(record.id);
            message.success('删除成功');
            refresh();
          }}
        >
          <a>删除</a>
        </Popconfirm>
      ),
    },
  ];

  // ── Tab 数据 ──────────────────────────────────────────────────────────

  const tabItems = frames.map((frame) => ({
    key: String(frame.id),
    label: frame.frameCode,
    children: (
      <div>
        {frame.frameCode.endsWith('-physical') ? (
          <ProTable<DATASOPHON.FrameServiceItem>
            rowKey="id"
            search={false}
            toolBarRender={false}
            dataSource={frame.frameServiceList ?? []}
            columns={physicalColumns}
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        ) : (
          <ProTable<DATASOPHON.FrameK8sServiceItem>
            rowKey="id"
            search={false}
            toolBarRender={false}
            dataSource={frame.frameK8sServiceList ?? []}
            columns={k8sColumns}
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        )}
      </div>
    ),
  }));

  // ── 渲染 ──────────────────────────────────────────────────────────────

  return (
    <PageContainer>
      {loading ? (
        <Spin />
      ) : frames.length === 0 ? (
        <Empty description="暂无数据" />
      ) : (
        <Tabs items={tabItems} />
      )}

      <Modal
        title={`编辑 DDL — ${ddlModal.serviceName}`}
        open={ddlModal.open}
        width={800}
        onOk={handleSaveDdl}
        onCancel={() => setDdlModal(INITIAL_DDL_MODAL)}
        confirmLoading={ddlModal.saving}
        okText="保存"
        cancelText="取消"
      >
        {ddlModal.loading ? (
          <Spin />
        ) : (
          <div
            style={{
              border: '1px solid #d9d9d9',
              borderRadius: 6,
              overflow: 'auto',
              maxHeight: 480,
              fontFamily: '"Fira Code", "Fira Mono", Consolas, monospace',
              fontSize: 13,
            }}
          >
            <Editor
              value={ddlModal.content}
              onValueChange={(content) =>
                setDdlModal((prev) => ({ ...prev, content }))
              }
              highlight={(code) =>
                hljs.highlight(code, { language: 'json', ignoreIllegals: true })
                  .value
              }
              padding={12}
              style={{ minHeight: 200 }}
            />
          </div>
        )}
      </Modal>
    </PageContainer>
  );
};

export default FrameManage;
