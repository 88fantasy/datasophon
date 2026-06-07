import { DeleteOutlined, PartitionOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  Button,
  Dropdown,
  message,
  Popconfirm,
  Progress,
  Space,
  Tag,
} from 'antd';
import React, { useContext, useRef, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import {
  deleteClusterHosts,
  listClusterHosts,
} from '@/services/datasophon/host';
import AssignRackModal from './components/AssignRackModal';
import RoleListModal from './components/RoleListModal';

const HOST_STATE_MAP: Record<number, { text: string; color: string }> = {
  1: { text: '正常', color: 'success' },
  2: { text: '掉线', color: 'error' },
  3: { text: '存在告警', color: 'warning' },
};

const CPU_ARCH_MAP: Record<string, string> = {
  x86_64: 'x86_64',
  aarch64: 'aarch64',
};

const UsageProgress: React.FC<{ used: number; total: number }> = ({
  used,
  total,
}) => {
  const percent = total > 0 ? Number(((used / total) * 100).toFixed(1)) : 0;
  return (
    <div>
      <div style={{ fontSize: 12 }}>
        {used}GB/{total}GB
      </div>
      <Progress
        percent={percent}
        status="active"
        showInfo={false}
        strokeColor={
          percent < 70 ? '#01AA72' : percent < 90 ? '#FF7E01' : '#FF5656'
        }
      />
    </div>
  );
};

const HostManage: React.FC = () => {
  const clusterCtx = useContext(ClusterContext);
  if (!clusterCtx) {
    throw new Error(
      'ClusterContext not found — HostManage must be rendered inside ClusterLayout',
    );
  }
  const { clusterId } = clusterCtx;
  const actionRef = useRef<ActionType>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [selectedRows, setSelectedRows] = useState<DATASOPHON.HostInfo[]>([]);

  const columns: ProColumns<DATASOPHON.HostInfo>[] = [
    {
      dataIndex: 'index',
      title: '序号',
      valueType: 'indexBorder',
      width: 48,
      search: false,
    },
    {
      title: '主机名',
      dataIndex: 'hostname',
      ellipsis: true,
      sorter: true,
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'hostState',
      ellipsis: true,
      search: false,
      render: (_, record) => {
        const state = HOST_STATE_MAP[record.hostState] ?? {
          text: '未知',
          color: 'default',
        };
        return <Tag color={state.color}>{state.text}</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '检查时间',
      dataIndex: 'checkTime',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '核数',
      dataIndex: 'coreNum',
      search: false,
    },
    {
      title: '内存使用',
      dataIndex: 'usedMem',
      search: false,
      sorter: true,
      render: (_, record) => (
        <UsageProgress
          used={record.usedMem ?? 0}
          total={record.totalMem ?? 0}
        />
      ),
    },
    {
      title: '磁盘使用',
      dataIndex: 'usedDisk',
      search: false,
      sorter: true,
      render: (_, record) => (
        <UsageProgress
          used={record.usedDisk ?? 0}
          total={record.totalDisk ?? 0}
        />
      ),
    },
    {
      title: '平均负载',
      dataIndex: 'averageLoad',
      search: false,
      sorter: true,
    },
    {
      title: '标签',
      dataIndex: 'nodeLabel',
      search: false,
    },
    {
      title: '机架',
      dataIndex: 'rack',
      search: false,
    },
    {
      title: 'CPU架构',
      dataIndex: 'cpuArchitecture',
      ellipsis: true,
      search: false,
      valueEnum: CPU_ARCH_MAP,
    },
    {
      title: '角色',
      dataIndex: 'serviceRoleNum',
      search: false,
      render: (_, record) => (
        <RoleListModal
          clusterId={clusterId}
          hostname={record.hostname}
          trigger={
            <Button type="link" size="small">
              {record.serviceRoleNum ?? 0}
            </Button>
          }
        />
      ),
    },
  ];

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请至少选择一台主机');
      return;
    }
    try {
      await deleteClusterHosts(clusterId, selectedRowKeys);
      message.success('删除成功');
      setSelectedRowKeys([]);
      setSelectedRows([]);
      actionRef.current?.reload();
    } catch {
      // error handler shows message
    }
  };

  const batchMenuItems = [
    {
      key: 'assign-rack',
      icon: <PartitionOutlined />,
      label: (
        <AssignRackModal
          clusterId={clusterId}
          hostIds={selectedRowKeys}
          trigger={<span>分配机架</span>}
          onSuccess={() => {
            actionRef.current?.reload();
          }}
        />
      ),
    },
    { type: 'divider' as const },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      label: (
        <Popconfirm
          title="确认删除所选主机？"
          onConfirm={handleBatchDelete}
          okText="确认"
          cancelText="取消"
        >
          <span style={{ color: '#ff4d4f' }}>删除</span>
        </Popconfirm>
      ),
    },
  ];

  return (
    <ProTable<DATASOPHON.HostInfo>
      actionRef={actionRef}
      rowKey="id"
      headerTitle="主机列表"
      search={{ filterType: 'light' }}
      params={{ clusterId }}
      request={async (params) => {
        const { current, pageSize, hostname, ip, sortField, sortOrder } =
          params;
        const result = await listClusterHosts(clusterId, {
          page: current ?? 1,
          pageSize: pageSize ?? 20,
          hostname,
          ip,
          sortField,
          sortOrder,
        });
        return {
          data: result.data.records ?? [],
          total: result.data.total ?? 0,
          success: true,
        };
      }}
      columns={columns}
      rowSelection={{
        selectedRowKeys,
        onChange: (keys, rows) => {
          setSelectedRowKeys(keys as number[]);
          setSelectedRows(rows);
        },
      }}
      tableAlertRender={({ selectedRowKeys }) => (
        <Space>
          已选择 {selectedRowKeys.length} 项
          <Dropdown menu={{ items: batchMenuItems }} trigger={['click']}>
            <Button size="small">批量操作</Button>
          </Dropdown>
        </Space>
      )}
      pagination={{ pageSize: 20 }}
      scroll={{ x: 1300 }}
    />
  );
};

export default HostManage;
