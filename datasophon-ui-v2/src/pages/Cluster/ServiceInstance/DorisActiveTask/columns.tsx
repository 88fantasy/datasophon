import type { ColumnsType } from 'antd/es/table';
import { formatBytes } from '@/pages/Cluster/Lineage/lineageFormatters';
import type { DorisActiveTask } from './types';

export const MISSING_VALUE = '–';
export const NOT_APPLICABLE_VALUE = '不适用';

export function isLoadTask(task: DorisActiveTask): boolean {
  return task.type.toUpperCase() === 'LOAD';
}

export function displayValue(
  value: string | number | null | undefined,
  notApplicable = false,
): string {
  if (notApplicable) return NOT_APPLICABLE_VALUE;
  return value == null || value === '' ? MISSING_VALUE : String(value);
}

function displayNumber(
  value: number | null | undefined,
  notApplicable = false,
): string {
  if (notApplicable) return NOT_APPLICABLE_VALUE;
  return value == null || !Number.isFinite(value)
    ? MISSING_VALUE
    : new Intl.NumberFormat('zh-CN').format(value);
}

function displayBytes(
  value: number | null | undefined,
  notApplicable = false,
): string {
  if (notApplicable) return NOT_APPLICABLE_VALUE;
  return value == null || !Number.isFinite(value)
    ? MISSING_VALUE
    : formatBytes(value);
}

function displayDuration(value: number | null | undefined): string {
  return value == null || !Number.isFinite(value)
    ? MISSING_VALUE
    : `${value} ms`;
}

function displayQueueDuration(task: DorisActiveTask): string {
  if (isLoadTask(task)) return NOT_APPLICABLE_VALUE;
  if (!task.queueStartTime || !task.queueEndTime) return MISSING_VALUE;
  const duration =
    new Date(task.queueEndTime).getTime() -
    new Date(task.queueStartTime).getTime();
  return Number.isFinite(duration) && duration >= 0
    ? `${duration} ms`
    : MISSING_VALUE;
}

export const ACTIVE_TASK_COLUMNS: ColumnsType<DorisActiveTask> = [
  {
    title: '类型',
    dataIndex: 'type',
    width: 90,
  },
  {
    title: '任务 ID',
    dataIndex: 'taskId',
    width: 220,
    ellipsis: true,
  },
  {
    title: '用户',
    dataIndex: 'user',
    width: 140,
    render: (_, task) => displayValue(task.user, isLoadTask(task)),
  },
  {
    title: '客户端地址',
    dataIndex: 'clientAddress',
    width: 180,
    render: (_, task) => displayValue(task.clientAddress, isLoadTask(task)),
  },
  {
    title: 'SQL',
    dataIndex: 'sql',
    width: 280,
    ellipsis: true,
    render: (_, task) => displayValue(task.sql, isLoadTask(task)),
  },
  {
    title: '已运行时长',
    dataIndex: 'elapsedMs',
    width: 130,
    render: (_, task) => displayDuration(task.elapsedMs),
  },
  {
    title: '当前内存',
    dataIndex: 'currentMemoryBytes',
    width: 130,
    render: (_, task) => displayBytes(task.currentMemoryBytes),
  },
  {
    title: '峰值内存（单 BE 最大）',
    dataIndex: 'peakMemoryBytes',
    width: 180,
    render: (_, task) => displayBytes(task.peakMemoryBytes),
  },
  {
    title: '扫描行数',
    dataIndex: 'scanRows',
    width: 130,
    render: (_, task) => displayNumber(task.scanRows),
  },
  {
    title: '扫描字节',
    dataIndex: 'scanBytes',
    width: 130,
    render: (_, task) => displayBytes(task.scanBytes, isLoadTask(task)),
  },
  {
    title: 'CPU 时间',
    dataIndex: 'cpuTimeMs',
    width: 120,
    render: (_, task) => displayDuration(task.cpuTimeMs),
  },
  {
    title: 'Workload Group',
    dataIndex: 'workloadGroupName',
    width: 180,
    render: (_, task) =>
      displayValue(task.workloadGroupName ?? task.workloadGroupId),
  },
  {
    title: '来源 FE',
    dataIndex: 'feHost',
    width: 160,
    render: (_, task) => displayValue(task.feHost),
  },
  {
    title: '排队状态（实验性）',
    dataIndex: 'queryStatus',
    width: 150,
    render: (_, task) => displayValue(task.queryStatus, isLoadTask(task)),
  },
  {
    title: '排队时长（实验性）',
    dataIndex: 'queueEndTime',
    width: 160,
    render: (_, task) => displayQueueDuration(task),
  },
];
