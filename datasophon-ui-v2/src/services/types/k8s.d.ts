declare namespace DATASOPHON {
  /** K8s 集群 namespace（对应后端 K8sClusterNamespace） */
  interface K8sNamespace {
    id: number;
    clusterId: number;
    /** -1 未知 / 0 inactive / 1 active */
    state: number;
    namespace: string;
  }

  /** K8s 服务实例（对应后端 K8sServiceInstanceVO） */
  interface K8sServiceInstanceVO {
    id: number;
    clusterId: number;
    namespaceId: number;
    namespace: string;
    /** ENVIRONMENT / MIDDLEWARE / APPLICATION */
    catalog: string;
    serviceId: number;
    serviceName: string;
    /** 0 初始化 / 1 成功 / 2 失败 */
    state: number;
  }

  /** K8s Helm values 完整记录（对应后端 K8sServiceInstanceValues） */
  interface K8sInstanceValues {
    id: number;
    clusterId: number;
    namespaceId: number;
    serviceId: number;
    instanceId: number;
    /** 原始 yaml 文本（base values） */
    values: string;
    /** 用户新增的配置 yaml（delta values） */
    deltaValues: string;
    version: number;
    /** 部署方式：helm / yaml */
    metaFileType: string;
  }

  /** K8s Helm values 版本列表项（listSimpleByInstanceId 只返回部分字段） */
  interface K8sInstanceValuesSimple {
    id: number;
    clusterId: number;
    namespaceId: number;
    serviceId: number;
    instanceId: number;
    version: number;
  }

  interface K8sDashboardResponse {
    observedAt: string;
    telemetry: { status: 'READY' | 'UNAVAILABLE'; message?: string };
    overview: {
      health: 'HEALTHY' | 'WARNING' | 'CRITICAL';
      readyNodes: number;
      totalNodes: number;
      runningPods: number;
      totalPods: number;
      critical: number;
      warning: number;
    };
    capacities: Array<{ name: string; percent?: number; used?: number; total?: number; unit: 'core' | 'byte' | 'count' }>;
    trends: Array<{
      timestamp: string;
      cpuPercent?: number;
      memoryPercent?: number;
      networkMbps?: number;
    }>;
    namespaces: Array<{
      name: string;
      podCount: number;
      cpuCores?: number;
      memoryBytes?: number;
    }>;
    workloads: Array<{
      name: string;
      namespace: string;
      type: string;
      ready: number;
      desired: number;
      status: string;
    }>;
    nodes: Array<{
      name: string;
      status: string;
      podCount: number;
      podCapacity: number;
      cpuPercent?: number;
      memoryPercent?: number;
      diskPercent?: number;
    }>;
    events: Array<{
      type: string;
      reason: string;
      namespace: string;
      object: string;
      message: string;
      lastTimestamp: string;
    }>;
  }
}
