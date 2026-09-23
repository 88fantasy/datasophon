export type SeaTunnelJobState = 'running' | 'finished';

export interface SeaTunnelOverview {
  projectVersion?: string | null;
  gitCommitAbbrev?: string | null;
  totalSlot?: string | null;
  runningJobs?: string | null;
  finishedJobs?: string | null;
  failedJobs?: string | null;
  pendingJobs?: string | null;
  cancelledJobs?: string | null;
  workers?: string | null;
}

export interface SeaTunnelWorker {
  address?: string | null;
  totalSlots?: string | null;
  usedSlots?: string | null;
  runningJobIds?: string[] | null;
  totalHeapMemoryBytes?: string | null;
}

export interface SeaTunnelWorkers {
  workers?: SeaTunnelWorker[] | null;
}

export interface SeaTunnelJob {
  jobId?: string | null;
  jobName?: string | null;
  jobStatus?: string | null;
  createTime?: string | null;
  finishTime?: string | null;
}

export interface SeaTunnelPendingJobs {
  queueSummary?: { size?: string | null } | null;
  pendingJobs?: SeaTunnelJob[] | null;
}

export interface SeaTunnelJobInfo extends SeaTunnelJob {
  // 表级指标（TableSourceReceivedCount 等）是 { 表名: 值 } 形式的嵌套对象
  metrics?: Record<string, unknown> | null;
}
