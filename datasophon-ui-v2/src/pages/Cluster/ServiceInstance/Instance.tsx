import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Badge, Button, Dropdown, message, Tag } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  getServiceRoleGroupList,
  getServiceRoleTypeList,
  listServiceRoleInstances,
} from '@/services/datasophon/service';

const serviceRoleStateMap: Record<number, string> = {
  1: '正在运行',
  2: '停止',
  3: '告警',
  4: '退役中',
  5: '已退役',
};

interface InstanceTabProps {
  clusterId: number;
  instanceId: number;
}

const InstanceTab: React.FC<InstanceTabProps> = ({ clusterId, instanceId }) => {
  const actionRef = useRef<any>(null);
  const [selectedRows, setSelectedRows] = useState<
    DATASOPHON.ServiceRoleInstanceInfo[]
  >([]);
  const [roleTypeEnum, setRoleTypeEnum] = useState<
    Record<number, { text: string }>
  >({});
  const [roleGroupEnum, setRoleGroupEnum] = useState<
    Record<number, { text: string }>
  >({});

  useEffect(() => {
    const fetchFilters = async () => {
      try {
        const [typeRes, groupRes] = await Promise.all([
          getServiceRoleTypeList(clusterId, instanceId),
          getServiceRoleGroupList(clusterId, instanceId),
        ]);
        const typeData = Array.isArray(typeRes)
          ? (typeRes as any)
          : ((typeRes as any).data ?? []);
        const groupData = Array.isArray(groupRes)
          ? (groupRes as any)
          : ((groupRes as any).data ?? []);
        setRoleTypeEnum(
          (typeData as { id: number; serviceRoleName: string }[]).reduce(
            (acc, cur) => {
              acc[cur.id] = { text: cur.serviceRoleName };
              return acc;
            },
            {} as Record<number, { text: string }>,
          ),
        );
        setRoleGroupEnum(
          (
            groupData as {
              id: number;
              roleGroupName: string;
            }[]
          ).reduce(
            (acc, cur) => {
              acc[cur.id] = { text: cur.roleGroupName };
              return acc;
            },
            {} as Record<number, { text: string }>,
          ),
        );
      } catch {
        /* global error handler */
      }
    };
    fetchFilters();
  }, [clusterId, instanceId]);

  const columns: ProColumns<DATASOPHON.ServiceRoleInstanceInfo>[] =
    useMemo(() => {
      return [
        {
          dataIndex: 'index',
          title: '序号',
          valueType: 'indexBorder',
          width: 48,
        },
        {
          title: '角色类型',
          dataIndex: 'serviceRoleName',
          ellipsis: true,
          valueEnum: roleTypeEnum,
          render: (text, record) => (
            <div>
              <Badge
                color={record.serviceRoleStateCode === 1 ? 'green' : 'red'}
                style={{ marginRight: 8 }}
              />
              {record.serviceRoleName}
            </div>
          ),
        },
        {
          title: '主机',
          dataIndex: 'hostname',
          ellipsis: true,
        },
        {
          title: '角色组',
          dataIndex: 'roleGroupName',
          valueEnum: roleGroupEnum,
          ellipsis: true,
        },
        {
          title: '状态',
          dataIndex: 'serviceRoleState',
          ellipsis: true,
          valueEnum: serviceRoleStateMap,
          render: (_, record) => {
            const colorMap: Record<number, 'success' | 'error' | 'warning'> = {
              1: 'success',
              2: 'error',
            };
            const stateStr =
              serviceRoleStateMap[record.serviceRoleStateCode] || '存在告警';
            const color = colorMap[record.serviceRoleStateCode] || 'warning';
            return <Tag color={color}>{stateStr}</Tag>;
          },
        },
        {
          title: '操作',
          valueType: 'option',
          key: 'option',
          width: 120,
          render: (_, record) => [
            <a
              key="log"
              onClick={() => {
                message.info('查看日志功能即将上线');
              }}
            >
              查看日志
            </a>,
          ],
        },
      ];
    }, [roleGroupEnum, roleTypeEnum]);

  const handleBatchOp = useCallback(
    async (commandType: string) => {
      if (!selectedRows.length) {
        message.warning('请至少选择一个实例');
        return;
      }
      message.info(`批量操作 ${commandType} 功能即将上线`);
    },
    [selectedRows],
  );

  const toolBarRender = useCallback(
    () => [
      <Dropdown
        key="batch"
        menu={{
          items: [
            {
              key: 'START_SERVICE',
              label: '启动',
              onClick: () => handleBatchOp('START_SERVICE'),
            },
            {
              key: 'STOP_SERVICE',
              label: '停止',
              onClick: () => handleBatchOp('STOP_SERVICE'),
            },
            {
              key: 'RESTART_SERVICE',
              label: '重启',
              onClick: () => handleBatchOp('RESTART_SERVICE'),
            },
            {
              key: 'assign-role-group',
              label: '分配角色组',
              onClick: () => handleBatchOp('ASSIGN_ROLE_GROUP'),
            },
            {
              key: 'delete',
              label: '删除',
              onClick: () => handleBatchOp('DELETE'),
            },
          ],
        }}
      >
        <Button>选择操作</Button>
      </Dropdown>,
      <Button key="add" type="primary" disabled>
        添加新实例
      </Button>,
      <Button key="add-group" type="primary" disabled>
        添加角色组
      </Button>,
    ],
    [handleBatchOp],
  );

  return (
    <ProTable<DATASOPHON.ServiceRoleInstanceInfo>
      actionRef={actionRef}
      rowKey="id"
      columns={columns}
      request={async (params) => {
        const { current, pageSize, ...rest } = params;
        const res = await listServiceRoleInstances(clusterId, instanceId, {
          page: current,
          pageSize,
          serviceRoleName: rest.serviceRoleName as string | undefined,
          roleGroupId: rest.roleGroupName as number | undefined,
        });
        const data = Array.isArray(res) ? (res as any) : (res as any).data;
        return {
          data: data?.data ?? [],
          success: true,
          total: data?.total ?? 0,
        };
      }}
      search={false}
      options={false}
      pagination={{ pageSize: 20 }}
      rowSelection={{
        selectedRowKeys: selectedRows.map((r) => r.id),
        onChange: (_keys, rows) => setSelectedRows(rows),
      }}
      tableAlertRender={false}
      toolbar={{
        settings: [],
      }}
      toolBarRender={toolBarRender}
    />
  );
};

export default InstanceTab;
