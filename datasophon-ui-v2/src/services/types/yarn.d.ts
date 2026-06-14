declare namespace DATASOPHON {
  /** YARN 队列，对应后端 ClusterYarnQueue 实体 */
  interface YarnQueue {
    id?: number;
    queueName: string;
    minCore: number;
    minMem: number;
    maxCore: number;
    maxMem: number;
    appNum: number;
    schedulePolicy: string;
    weight: number;
    /** 1 = 是，2 = 否 */
    allowPreemption: number;
    amShare: string;
    clusterId?: number;
    createTime?: string;
  }

  /** 新建 YARN 队列请求体（v2），对应后端 SaveYarnQueueRequest */
  interface SaveYarnQueueRequest {
    queueName: string;
    minCore: number;
    minMem: number;
    maxCore: number;
    maxMem: number;
    appNum: number;
    schedulePolicy: string;
    weight: number;
    /** 1 = 是，2 = 否 */
    allowPreemption: number;
    amShare: string;
  }

  /** 修改 YARN 队列请求体（v2），对应后端 UpdateYarnQueueRequest */
  interface UpdateYarnQueueRequest extends SaveYarnQueueRequest {
    id: number;
  }

  /** YARN 队列分页响应体（v2），对应后端 YarnQueuePageResponse */
  interface YarnQueuePageResponse {
    data: YarnQueue[];
    total: number;
  }
}
