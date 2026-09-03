export interface DorisActiveTaskQuery {
  keyword?: string | null;
  types?: string[] | null;
  user?: string | null;
  feHost?: string | null;
  minMemoryBytes?: number | null;
  minElapsedMs?: number | null;
}

export interface DorisBeTaskDetail {
  beId: string;
  peakMemoryBytes?: number | null;
  currentMemoryBytes?: number | null;
  scanRows?: number | null;
  scanBytes?: number | null;
}

export interface DorisActiveTask {
  taskId: string;
  type: string;
  user?: string | null;
  clientAddress?: string | null;
  sql?: string | null;
  detailSql?: string | null;
  elapsedMs?: number | null;
  startTime?: string | null;
  currentMemoryBytes?: number | null;
  peakMemoryBytes?: number | null;
  scanRows?: number | null;
  scanBytes?: number | null;
  cpuTimeMs?: number | null;
  shuffleSendBytes?: number | null;
  shuffleSendRows?: number | null;
  spillWriteBytesToLocalStorage?: number | null;
  spillReadBytesFromLocalStorage?: number | null;
  workloadGroupId?: number | null;
  workloadGroupName?: string | null;
  feHost?: string | null;
  queryStatus?: string | null;
  queueStartTime?: string | null;
  queueEndTime?: string | null;
  truncated?: boolean | null;
  beDetails?: DorisBeTaskDetail[] | null;
}

export interface DorisActiveTaskResponse {
  tasks: DorisActiveTask[];
  degraded: boolean;
  degradedReason?: string | null;
  partialFailures: string[];
  truncated: boolean;
  sourceTruncated: boolean;
  total: number;
  returned: number;
  connectedHostPort: string;
}
