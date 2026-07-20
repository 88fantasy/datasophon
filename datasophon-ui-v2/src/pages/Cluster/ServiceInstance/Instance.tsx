import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { App, Badge, Button, Dropdown, Tag, Tooltip } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  deleteRoleInstances,
  execRoleCommand,
  getServiceRoleGroupList,
  getServiceRoleTypeList,
  listServiceRoleInstances,
} from '@/services/service';
import AddRoleGroupModal from './components/AddRoleGroupModal';
import AssignRoleGroupModal from './components/AssignRoleGroupModal';
import LogModal from './components/LogModal';

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
  const { message, modal } = App.useApp();
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

  // 弹窗状态
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [logRecord, setLogRecord] =
    useState<DATASOPHON.ServiceRoleInstanceInfo | null>(null);
  const [addGroupOpen, setAddGroupOpen] = useState(false);
  const [assignGroupOpen, setAssignGroupOpen] = useState(false);

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
          (groupData as { id: number; roleGroupName: string }[]).reduce(
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

  /** 通用批量操作守卫：无选中行则弹提示，有则执行 fn */
  const withSelection = useCallback(
    async (fn: () => Promise<void>) => {
      if (!selectedRows.length) {
        message.warning('请至少选择一个实例');
        return;
      }
      await fn();
    },
    [message, selectedRows.length],
  );

  /** 批量启/停/重启 */
  const handleCommand = useCallback(
    (commandType: string) => {
      withSelection(async () => {
        const confirmed = await new Promise<boolean>((resolve) => {
          modal.confirm({
            title: `确认${commandType === 'START_SERVICE' ? '启动' : commandType === 'STOP_SERVICE' ? '停止' : '重启'}所选实例？`,
            onOk: () => resolve(true),
            onCancel: () => resolve(false),
          });
        });
        if (!confirmed) return;
        try {
          await execRoleCommand(clusterId, instanceId, {
            commandType,
            serviceRoleInstancesIds: selectedRows.map((r) => r.id).join(','),
          });
          message.success('操作已下发');
          setSelectedRows([]);
          actionRef.current?.reload();
        } catch {
          message.error('操作失败');
        }
      });
    },
    [withSelection, modal, clusterId, instanceId, selectedRows, message],
  );

  /** 批量删除 */
  const handleDelete = useCallback(() => {
    withSelection(async () => {
      const confirmed = await new Promise<boolean>((resolve) => {
        modal.confirm({
          title: '确认删除所选实例？',
          okType: 'danger',
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!confirmed) return;
      try {
        await deleteRoleInstances(
          clusterId,
          instanceId,
          selectedRows.map((r) => r.id).join(','),
        );
        message.success('删除成功');
        setSelectedRows([]);
        actionRef.current?.reload();
      } catch {
        message.error('删除失败');
      }
    });
  }, [withSelection, modal, clusterId, instanceId, selectedRows, message]);

  const columns: ProColumns<DATASOPHON.ServiceRoleInstanceInfo>[] = useMemo(
    () => [
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
        render: (_, record) => (
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
        title: '端口',
        dataIndex: 'ports',
        search: false,
        render: (_, record) =>
          record.ports?.length ? (
            record.ports.map((p) => (
              <Tooltip key={p.paramName} title={p.label}>
                <Tag>{p.port}</Tag>
              </Tooltip>
            ))
          ) : (
            <span>-</span>
          ),
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
              setLogRecord(record);
              setLogModalOpen(true);
            }}
          >
            查看日志
          </a>,
        ],
      },
    ],
    [roleGroupEnum, roleTypeEnum],
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
              onClick: () => handleCommand('START_SERVICE'),
            },
            {
              key: 'STOP_SERVICE',
              label: '停止',
              onClick: () => handleCommand('STOP_SERVICE'),
            },
            {
              key: 'RESTART_SERVICE',
              label: '重启',
              onClick: () => handleCommand('RESTART_SERVICE'),
            },
            {
              key: 'assign-role-group',
              label: '分配角色组',
              onClick: () =>
                withSelection(async () => {
                  setAssignGroupOpen(true);
                }),
            },
            {
              key: 'delete',
              label: '删除',
              onClick: handleDelete,
            },
          ],
        }}
      >
        <Button>选择操作</Button>
      </Dropdown>,
      <Button
        key="add-group"
        type="primary"
        onClick={() => setAddGroupOpen(true)}
      >
        添加角色组
      </Button>,
      <Tooltip key="add-instance" title="扩容向导即将上线">
        <Button type="primary" disabled>
          添加新实例
        </Button>
      </Tooltip>,
    ],
    [handleCommand, handleDelete, withSelection],
  );

  return (
    <>
      <ProTable<DATASOPHON.ServiceRoleInstanceInfo>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        params={{ clusterId, instanceId }}
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
        toolbar={{ settings: [] }}
        toolBarRender={toolBarRender}
      />

      <LogModal
        open={logModalOpen}
        record={logRecord}
        clusterId={clusterId}
        instanceId={instanceId}
        onClose={() => setLogModalOpen(false)}
      />

      <AddRoleGroupModal
        open={addGroupOpen}
        clusterId={clusterId}
        instanceId={instanceId}
        onClose={() => setAddGroupOpen(false)}
        onSuccess={() => {
          setAddGroupOpen(false);
          // 刷新角色组筛选枚举
          getServiceRoleGroupList(clusterId, instanceId).then((res) => {
            const list = (res as any)?.data ?? [];
            setRoleGroupEnum(
              (list as { id: number; roleGroupName: string }[]).reduce(
                (acc, cur) => {
                  acc[cur.id] = { text: cur.roleGroupName };
                  return acc;
                },
                {} as Record<number, { text: string }>,
              ),
            );
          });
        }}
      />

      <AssignRoleGroupModal
        open={assignGroupOpen}
        clusterId={clusterId}
        instanceId={instanceId}
        selectedIds={selectedRows.map((r) => r.id)}
        onClose={() => setAssignGroupOpen(false)}
        onSuccess={() => {
          setAssignGroupOpen(false);
          setSelectedRows([]);
          actionRef.current?.reload();
        }}
      />
    </>
  );
};

export default InstanceTab;
