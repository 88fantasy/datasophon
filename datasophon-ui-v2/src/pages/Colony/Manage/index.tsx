import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Button, Col, Popconfirm, Row, Space, Tag, Typography, message } from 'antd';
import React from 'react';
import { deleteCluster, listClusters } from '@/services/datasophon/cluster';
import AuthModal from './AuthModal';
import BuildOrEditModal from './BuildOrEditModal';

const STATE_COLOR: Record<string, string> = {
  正在运行: 'success',
  停止: 'default',
  告警: 'warning',
  安装中: 'processing',
  停止中: 'default',
  启动中: 'processing',
};

const ColonyManage: React.FC = () => {
  // useRequest auto-unwraps { data: ClusterInfo[] } → ClusterInfo[]
  const { data: clusters, refresh, loading } = useRequest(listClusters);
  const clusterList = clusters ?? [];

  const handleDelete = async (id: number) => {
    try {
      await deleteCluster(id);
      message.success('集群已删除');
      refresh();
    } catch {
      // errorHandler already shows message
    }
  };

  return (
    <PageContainer>
      <Row gutter={[16, 16]}>
        {clusterList.map((cluster) => (
          <Col key={cluster.id} xs={24} sm={12} lg={8}>
            <ProCard
              loading={loading}
              title={
                <Space>
                  <Typography.Text strong>{cluster.clusterName}</Typography.Text>
                  {cluster.clusterState && (
                    <Tag color={STATE_COLOR[cluster.clusterState] ?? 'default'}>
                      {cluster.clusterState}
                    </Tag>
                  )}
                </Space>
              }
              extra={
                <Space>
                  <BuildOrEditModal
                    cluster={cluster}
                    onSuccess={refresh}
                    trigger={
                      <Button type="link" size="small" icon={<EditOutlined />} />
                    }
                  />
                  <AuthModal
                    cluster={cluster}
                    onSuccess={refresh}
                    trigger={
                      <Button type="link" size="small" icon={<TeamOutlined />} />
                    }
                  />
                  <Popconfirm
                    title="确认删除该集群？"
                    onConfirm={() => handleDelete(cluster.id)}
                    okText="确认"
                    cancelText="取消"
                  >
                    <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              }
              hoverable
              style={{ height: '100%', border: '1px solid #d9d9d9' }}
            >
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
                    {cluster.clusterManagerList!.map((m) => m.username).join('、')}
                  </Typography.Text>
                </div>
              )}
            </ProCard>
          </Col>
        ))}

        {/* 新建集群卡片 */}
        <Col xs={24} sm={12} lg={8}>
          <BuildOrEditModal
            onSuccess={refresh}
            trigger={
              <ProCard
                style={{
                  height: '100%',
                  minHeight: 120,
                  cursor: 'pointer',
                  border: '1px solid #d9d9d9',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: 80,
                  }}
                >
                  <Space direction="vertical" align="center">
                    <PlusOutlined style={{ fontSize: 24, color: '#999' }} />
                    <Typography.Text type="secondary">新建集群</Typography.Text>
                  </Space>
                </div>
              </ProCard>
            }
          />
        </Col>
      </Row>
    </PageContainer>
  );
};

export default ColonyManage;
