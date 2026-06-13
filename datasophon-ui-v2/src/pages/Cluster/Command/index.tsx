import ClusterContext from '@/context/ClusterContext';
import { listDagCommands } from '@/services/dag';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Badge } from 'antd';
import React, { useCallback, useContext, useEffect, useRef } from 'react';

type DagStatus = DATASOPHON.DagStatus;
type DagCommand = DATASOPHON.DagCommand;

const STATUS_CONFIG: Record<
  DagStatus,
  { color: string; text: string; processing?: boolean }
> = {
  SUCCESS: { color: 'green', text: '成功' },
  FAILED: { color: 'red', text: '失败' },
  CANCEL: { color: 'gold', text: '已取消' },
  PENDING: { color: 'blue', text: '等待中' },
  RUNNING: { color: 'blue', text: '运行中', processing: true },
};

const CommandList: React.FC = () => {
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;
  const actionRef = useRef<ActionType>(null);
  const timerRef = useRef<number | undefined>(undefined);

  const cancelTimer = useCallback(() => {
    if (timerRef.current !== undefined) {
      clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }, []);

  /** 当有进行中命令时启动 2s 轮询 */
  const scheduleRefresh = useCallback(
    (hasActive: boolean) => {
      cancelTimer();
      if (hasActive) {
        timerRef.current = window.setTimeout(() => {
          actionRef.current?.reload();
        }, 2000);
      }
    },
    [cancelTimer],
  );

  useEffect(() => {
    return () => cancelTimer();
  }, [cancelTimer]);

  const columns: ProColumns<DagCommand>[] = [
    {
      dataIndex: 'index',
      title: '序号',
      valueType: 'indexBorder',
      width: 48,
    },
    {
      title: '名称',
      dataIndex: 'dagName',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const cfg = STATUS_CONFIG[record.status] ?? STATUS_CONFIG.PENDING;
        return (
          <span>
            <Badge
              color={cfg.color}
              status={cfg.processing ? 'processing' : undefined}
            />
            {cfg.text}
          </span>
        );
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createdTime',
      width: 160,
    },
    {
      title: '开始时间',
      dataIndex: 'startedTime',
      width: 160,
    },
    {
      title: '完成时间',
      dataIndex: 'completedTime',
      width: 160,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, record) => [
        <a
          key="view"
          onClick={() => {
            window.open(`/ddh/cluster/${clusterId}/dag/${record.id}`);
          }}
        >
          查看
        </a>,
      ],
    },
  ];

  return (
    <ProTable<DagCommand>
      rowKey="id"
      headerTitle="命令历史"
      actionRef={actionRef}
      search={false}
      scroll={{ y: '60vh' }}
      columns={columns}
      tableAlertRender={false}
      request={async (params) => {
        const res = await listDagCommands(clusterId, {
          page: params.current,
          pageSize: params.pageSize,
        });
        const list: DagCommand[] = (res as any)?.data?.records ?? [];
        const total: number = (res as any)?.data?.total ?? 0;
        const hasActive = list.some(
          (r) => r.status === 'PENDING' || r.status === 'RUNNING',
        );
        scheduleRefresh(hasActive);
        return { data: list, success: true, total };
      }}
    />
  );
};

export default CommandList;
