import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Table,
  Typography,
} from 'antd';
import { formatBytes } from '@/pages/Cluster/Lineage/lineageFormatters';
import { displayValue, NOT_APPLICABLE_VALUE } from './columns';
import type { DorisActiveTask, DorisBeTaskDetail } from './types';

interface TaskDetailDrawerProps {
  task?: DorisActiveTask;
  open: boolean;
  onClose: () => void;
}

function displayBytes(
  value: number | null | undefined,
  notApplicable = false,
): string {
  if (notApplicable) return NOT_APPLICABLE_VALUE;
  return value == null || !Number.isFinite(value) ? '–' : formatBytes(value);
}

function displayNumber(value: number | null | undefined): string {
  return value == null ? '–' : new Intl.NumberFormat('zh-CN').format(value);
}

function displayDuration(value: number | null | undefined): string {
  return value == null || !Number.isFinite(value) ? '–' : `${value} ms`;
}

function beColumns(isLoad: boolean) {
  return [
    {
      title: 'BE ID',
      dataIndex: 'beId',
      render: (value: string | null | undefined) => displayValue(value),
    },
    {
      title: '峰值内存',
      dataIndex: 'peakMemoryBytes',
      render: (value: number | null | undefined) => displayBytes(value),
    },
    {
      title: '当前内存',
      dataIndex: 'currentMemoryBytes',
      render: (value: number | null | undefined) => displayBytes(value),
    },
    {
      title: '扫描行数',
      dataIndex: 'scanRows',
      render: (value: number | null | undefined) => displayNumber(value),
    },
    {
      title: '扫描字节',
      dataIndex: 'scanBytes',
      render: (value: number | null | undefined) =>
        displayBytes(value, isLoad),
    },
  ];
}

const TaskDetailDrawer: React.FC<TaskDetailDrawerProps> = ({
  task,
  open,
  onClose,
}) => {
  const isLoad = task?.type.toUpperCase() === 'LOAD';
  const returnedSql = task?.detailSql ?? task?.sql;

  const copyReturnedSql = async () => {
    if (!returnedSql) return;
    await navigator.clipboard.writeText(returnedSql);
  };

  return (
    <Drawer
      title={task ? `活动任务：${task.taskId}` : '活动任务详情'}
      open={open}
      onClose={onClose}
      size={560}
    >
      {!task ? (
        <Empty description="未选择任务" />
      ) : (
        <>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="类型">
              {displayValue(task.type)}
            </Descriptions.Item>
            <Descriptions.Item label="任务 ID">
              {displayValue(task.taskId)}
            </Descriptions.Item>
            <Descriptions.Item label="用户">
              {displayValue(task.user, isLoad)}
            </Descriptions.Item>
            <Descriptions.Item label="客户端地址">
              {displayValue(task.clientAddress, isLoad)}
            </Descriptions.Item>
            <Descriptions.Item label="来源 FE">
              {displayValue(task.feHost)}
            </Descriptions.Item>
            <Descriptions.Item label="Workload Group">
              {displayValue(task.workloadGroupName ?? task.workloadGroupId)}
            </Descriptions.Item>
            <Descriptions.Item label="开始时间">
              {displayValue(task.startTime, isLoad)}
            </Descriptions.Item>
            <Descriptions.Item label="已运行时长">
              {displayDuration(task.elapsedMs)}
            </Descriptions.Item>
            <Descriptions.Item label="当前内存">
              {displayBytes(task.currentMemoryBytes)}
            </Descriptions.Item>
            <Descriptions.Item label="峰值内存（单 BE 最大）">
              {displayBytes(task.peakMemoryBytes)}
            </Descriptions.Item>
            <Descriptions.Item label="CPU 时间">
              {displayValue(
                task.cpuTimeMs == null ? null : `${task.cpuTimeMs} ms`,
              )}
            </Descriptions.Item>
            <Descriptions.Item label="扫描行数">
              {displayNumber(task.scanRows)}
            </Descriptions.Item>
            <Descriptions.Item label="扫描字节">
              {displayBytes(task.scanBytes, isLoad)}
            </Descriptions.Item>
            <Descriptions.Item label="Shuffle 发送字节">
              {displayBytes(task.shuffleSendBytes)}
            </Descriptions.Item>
            <Descriptions.Item label="Shuffle 发送行数">
              {displayNumber(task.shuffleSendRows)}
            </Descriptions.Item>
            <Descriptions.Item label="Spill 写入本地存储">
              {displayBytes(task.spillWriteBytesToLocalStorage)}
            </Descriptions.Item>
            <Descriptions.Item label="Spill 读取本地存储">
              {displayBytes(task.spillReadBytesFromLocalStorage)}
            </Descriptions.Item>
          </Descriptions>

          {!isLoad && returnedSql != null ? (
            <section data-testid="doris-active-task-sql">
              <Typography.Title level={5}>SQL</Typography.Title>
              {task.truncated ? (
                <Alert
                  type="warning"
                  showIcon
                  title="SQL 已截断，复制内容仅包含当前返回内容"
                />
              ) : null}
              <Typography.Paragraph copyable={false} ellipsis={false}>
                <pre>{returnedSql}</pre>
              </Typography.Paragraph>
              <Button onClick={() => void copyReturnedSql()}>
                复制当前返回内容
              </Button>
            </section>
          ) : null}

          <Typography.Title level={5}>各 BE 资源明细</Typography.Title>
          <Table<DorisBeTaskDetail>
            rowKey={(detail, index) => `${detail.beId ?? 'unknown'}-${index}`}
            size="small"
            pagination={false}
            dataSource={task.beDetails ?? []}
            columns={beColumns(isLoad)}
          />
        </>
      )}
    </Drawer>
  );
};

export default TaskDetailDrawer;
