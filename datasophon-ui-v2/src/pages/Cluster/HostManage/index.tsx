import {
  DeleteOutlined,
  PartitionOutlined,
  TagsOutlined,
} from '@ant-design/icons';
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
import { deleteClusterHosts, listClusterHosts } from '@/services/host';
import AssignLabelModal from './components/AssignLabelModal';
import AssignRackModal from './components/AssignRackModal';
import LabelManageModal from './components/LabelManageModal';
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
  const [hostSummary, setHostSummary] = useState({
    total: 0,
    healthy: 0,
    abnormal: 0,
  });
  const [_selectedRows, setSelectedRows] = useState<DATASOPHON.HostResponse[]>(
    [],
  );

  const columns: ProColumns<DATASOPHON.HostResponse>[] = [
    {
      dataIndex: 'index',
      title: '序号',
      valueType: 'indexBorder',
      width: 48,
      fixed: 'left',
      search: false,
    },
    {
      title: '主机名',
      dataIndex: 'hostname',
      ellipsis: true,
      sorter: true,
      width: 120,
      fixed: 'left',
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      width: 140,
      fixed: 'left',
    },
    {
      title: '状态',
      dataIndex: 'hostState',
      ellipsis: true,
      width: 88,
      search: false,
      render: (_, record) => {
        const state = HOST_STATE_MAP[record.hostState ?? -1] ?? {
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
      width: 168,
      search: false,
    },
    {
      title: '检查时间',
      dataIndex: 'checkTime',
      valueType: 'dateTime',
      width: 168,
      search: false,
    },
    {
      title: '核数',
      dataIndex: 'coreNum',
      width: 72,
      search: false,
    },
    {
      title: '内存使用',
      dataIndex: 'usedMem',
      width: 132,
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
      width: 132,
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
      width: 92,
      search: false,
      sorter: true,
    },
    {
      title: '标签',
      dataIndex: 'nodeLabel',
      width: 100,
      search: false,
    },
    {
      title: '机架',
      dataIndex: 'rack',
      width: 112,
      search: false,
    },
    {
      title: 'CPU架构',
      dataIndex: 'cpuArchitecture',
      ellipsis: true,
      width: 100,
      search: false,
      valueEnum: CPU_ARCH_MAP,
    },
    {
      title: '角色',
      dataIndex: 'serviceRoleNum',
      width: 72,
      fixed: 'right',
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
    {
      key: 'assign-label',
      label: (
        <AssignLabelModal
          clusterId={clusterId}
          hostIds={selectedRowKeys}
          trigger={<span>分配标签</span>}
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
    <ProTable<DATASOPHON.HostResponse>
      style={{ minHeight: '100%' }}
      cardProps={{ style: { minHeight: '100%' } }}
      actionRef={actionRef}
      rowKey="id"
      headerTitle={
        <Space size={8}>
          <span>主机列表</span>
          <Tag color="blue">共 {hostSummary.total} 台</Tag>
          <Tag color="success">当前页正常 {hostSummary.healthy}</Tag>
          {hostSummary.abnormal > 0 && (
            <Tag color="warning">当前页异常 {hostSummary.abnormal}</Tag>
          )}
        </Space>
      }
      toolBarRender={() => [
        <LabelManageModal
          key="label-manage"
          clusterId={clusterId}
          trigger={<Button icon={<TagsOutlined />}>标签管理</Button>}
        />,
      ]}
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
        const rows = result.data.records ?? [];
        setHostSummary({
          total: result.data.total ?? rows.length,
          healthy: rows.filter((host) => host.hostState === 1).length,
          abnormal: rows.filter((host) => host.hostState !== 1).length,
        });
        return {
          data: rows,
          total: result.data.total ?? 0,
          success: true,
        };
      }}
      columns={columns}
      locale={{ emptyText: '暂无主机' }}
      columnsState={{
        defaultValue: {
          createTime: { show: false },
          nodeLabel: { show: false },
          rack: { show: false },
          cpuArchitecture: { show: false },
        },
        persistenceKey: `cluster-${clusterId}-host-columns`,
        persistenceType: 'localStorage',
      }}
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
      pagination={{ pageSize: 20, showSizeChanger: false }}
      scroll={{ x: 1420 }}
      size="small"
    />
  );
};

export default HostManage;
