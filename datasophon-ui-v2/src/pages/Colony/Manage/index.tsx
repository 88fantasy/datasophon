import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  RightCircleOutlined,
  SettingOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ProListProps } from '@ant-design/pro-components';
import { PageContainer, ProList } from '@ant-design/pro-components';
import { history, useRequest } from '@umijs/max';
import {
  Button,
  Dropdown,
  message,
  Popconfirm,
  Space,
  Tag,
  Typography,
} from 'antd';
import React, { useState } from 'react';
import { deleteCluster, listClusters } from '@/services/cluster';
import AuthModal from './AuthModal';
import BuildOrEditModal from './BuildOrEditModal';
import ConfigModalK8s from './ConfigModalK8s';
import ConfigModalPhysical from './ConfigModalPhysical';
import ImportManifestModal from './ImportManifestModal';
import ImportPackageModal from './ImportPackageModal';

const STATE_COLOR: Record<string, string> = {
  正在运行: 'success',
  停止: 'default',
  告警: 'warning',
  安装中: 'processing',
  停止中: 'default',
  启动中: 'processing',
};

type ModalKey = 'package' | 'manifest' | 'k8sConfig' | 'physicalConfig';

interface OpenState {
  modal: ModalKey | null;
  cluster: DATASOPHON.ClusterResponse | null;
}

const ColonyManage: React.FC = () => {
  const { data: clusters, refresh, loading } = useRequest(listClusters);
  const clusterList = clusters ?? [];

  const [openState, setOpenState] = useState<OpenState>({
    modal: null,
    cluster: null,
  });

  const openModal = (modal: ModalKey, cluster: DATASOPHON.ClusterResponse) =>
    setOpenState({ modal, cluster });
  const closeModal = () => setOpenState({ modal: null, cluster: null });

  const handleDelete = async (id: number) => {
    try {
      await deleteCluster(id);
      message.success('集群已删除');
      refresh();
    } catch {
      // errorHandler already shows message
    }
  };

  const columns: ProListProps<DATASOPHON.ClusterResponse>['columns'] = [
    {
      dataIndex: 'clusterName',
      listSlot: 'title',
      render: (_, cluster) => (
        <Space>
          <Typography.Text strong>{cluster.clusterName}</Typography.Text>
          {cluster.clusterState && (
            <Tag color={STATE_COLOR[cluster.clusterState] ?? 'default'}>
              {cluster.clusterState}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      dataIndex: 'clusterCode',
      listSlot: 'description',
      render: (_, cluster) => (
        <>
          <Typography.Text type="secondary">
            {cluster.clusterCode}
          </Typography.Text>
          {cluster.clusterFrame && (
            <div style={{ marginTop: 4 }}>
              <Tag>{cluster.clusterFrame}</Tag>
              {cluster.archType && (
                <Tag color={cluster.archType === 'k8s' ? 'blue' : 'geekblue'}>
                  {cluster.archType === 'k8s' ? 'K8s' : '物理集群'}
                </Tag>
              )}
            </div>
          )}
          {(cluster.clusterManagerList?.length ?? 0) > 0 && (
            <div style={{ marginTop: 8 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                管理员：
                {cluster.clusterManagerList?.map((m) => m.username).join('、')}
              </Typography.Text>
            </div>
          )}
        </>
      ),
    },
    {
      dataIndex: 'id',
      listSlot: 'actions',
      valueType: 'option',
      render: (_, cluster) => {
        const isRunning = cluster.clusterStateCode === 2;
        const isPhysical = !cluster.archType || cluster.archType === 'physical';
        const isK8s = cluster.archType === 'k8s';

        return [
          <Button
            key="enter"
            type="link"
            size="small"
            icon={<RightCircleOutlined />}
            onClick={() => history.push(`/cluster/${cluster.id}/host`)}
          >
            进入
          </Button>,
          <BuildOrEditModal
            key="edit"
            cluster={cluster}
            onSuccess={refresh}
            trigger={
              <Button type="link" size="small" icon={<EditOutlined />} />
            }
          />,
          isPhysical && (
            <AuthModal
              key="auth"
              cluster={cluster}
              onSuccess={refresh}
              trigger={
                <Button type="link" size="small" icon={<TeamOutlined />} />
              }
            />
          ),
          isPhysical && isRunning && (
            <Dropdown
              key="import"
              menu={{
                items: [
                  {
                    key: 'package',
                    label: '部署包',
                    onClick: () => openModal('package', cluster),
                  },
                  {
                    key: 'manifest',
                    label: '部署清单',
                    onClick: () => openModal('manifest', cluster),
                  },
                ],
              }}
            >
              <Button type="link" size="small" icon={<UploadOutlined />}>
                导入
              </Button>
            </Dropdown>
          ),
          <Button
            key="config"
            type="link"
            size="small"
            icon={<SettingOutlined />}
            disabled={isPhysical && isRunning}
            onClick={() => {
              if (isK8s) openModal('k8sConfig', cluster);
              if (isPhysical) openModal('physicalConfig', cluster);
            }}
          >
            配置
          </Button>,
          <Popconfirm
            key="delete"
            title="确认删除该集群？"
            onConfirm={() => handleDelete(cluster.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>,
        ].filter(Boolean);
      },
    },
  ];

  return (
    <PageContainer>
      <ProList<DATASOPHON.ClusterResponse>
        rowKey="id"
        dataSource={clusterList}
        loading={loading}
        grid={{ gutter: 8, column: 2 }}
        columns={columns}
        pagination={false}
        search={false}
        options={false}
        toolBarRender={() => [
          <BuildOrEditModal
            key="create"
            onSuccess={refresh}
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                新建集群
              </Button>
            }
          />,
        ]}
      />

      {openState.cluster && openState.modal === 'package' && (
        <ImportPackageModal
          cluster={openState.cluster}
          open
          onClose={closeModal}
        />
      )}
      {openState.cluster && openState.modal === 'manifest' && (
        <ImportManifestModal
          cluster={openState.cluster}
          open
          onClose={closeModal}
        />
      )}
      {openState.cluster && openState.modal === 'k8sConfig' && (
        <ConfigModalK8s
          cluster={openState.cluster}
          open
          onClose={closeModal}
          onSuccess={() => {
            closeModal();
            refresh();
          }}
        />
      )}
      {openState.cluster && openState.modal === 'physicalConfig' && (
        <ConfigModalPhysical
          cluster={openState.cluster}
          open
          onClose={closeModal}
          onSuccess={() => {
            closeModal();
            refresh();
          }}
        />
      )}
    </PageContainer>
  );
};

export default ColonyManage;
