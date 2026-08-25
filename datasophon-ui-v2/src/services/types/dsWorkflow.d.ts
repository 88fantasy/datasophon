declare namespace DATASOPHON {
  interface DsPage<T> {
    list: T[];
    total: number;
    pageNo: number;
    pageSize: number;
  }

  interface DsProject {
    code: number;
    name: string;
    description?: string;
    owner?: string;
  }

  interface DsWorkflowDefinition {
    code: number;
    name: string;
    version: number;
    releaseState: string;
    owner?: string;
    description?: string;
    updateTime?: string;
  }

  interface DsWorkflowInstance {
    id: number;
    workflowCode: number;
    name: string;
    state: string;
    startTime?: string;
    endTime?: string;
    durationSeconds: number;
    host?: string;
    commandType?: string;
    dryRun: boolean;
  }

  interface DsBatchOutput {
    namespace: string;
    name: string;
    rowCount?: number;
    size?: number;
    jobName?: string;
  }

  interface DsTaskMetrics {
    kind: 'BATCH' | 'STREAM';
    runCount?: number;
    outputs?: DsBatchOutput[];
    jobId?: string;
    jobName?: string;
    rowsPerSecond?: number;
    approximate?: boolean;
    processedApprox?: number;
    since?: string;
  }

  interface DsDagNode {
    taskCode: number;
    name: string;
    taskType: string;
    taskExecuteType?: string;
    flowType: 'BATCH' | 'STREAM';
    taskInstanceId?: number;
    state?: string;
    startTime?: string;
    endTime?: string;
    durationSeconds: number;
    host?: string;
    retryTimes: number;
    metrics?: DsTaskMetrics;
    metricsError?: 'NOT_BOUND' | 'LOOKUP_FAILED';
  }

  interface DsDagEdge {
    from: number;
    to: number;
  }

  interface DsDagLocation {
    taskCode: number;
    x: number;
    y: number;
  }

  interface DsDag {
    instance: DsWorkflowInstance;
    nodes: DsDagNode[];
    edges: DsDagEdge[];
    locations: DsDagLocation[];
  }
}
