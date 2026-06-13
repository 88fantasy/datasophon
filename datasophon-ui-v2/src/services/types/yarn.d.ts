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
}
