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
    /** INSTALLED=平台安装 / IMPORTED=接管登记 */
    source?: string;
    /** 接管实例对应的 helm release 名 */
    releaseName?: string;
    /** 指标 job（Doris service_name），多个以英文逗号分隔 */
    metricsJob?: string;
    /** 来源类型 HELM=Helm release / CR=Operator 自定义资源，默认 HELM */
    sourceKind?: string;
    /** 看板画像 JSON（模式判定 + 角色→job 映射）原文，CR 来源专用，前端自行 JSON.parse */
    monitorProfile?: string;
    /** 轻对账结果：对应的 Helm release 已不在目标集群中（仅接管实例会被赋值） */
    missing?: boolean;
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

  /** 接管扫描出的单个 Helm release */
  interface ScannedRelease {
    releaseName: string;
    namespace: string;
    chart: string;
    chartName: string;
    chartVersion?: string;
    appVersion?: string;
    /** 已匹配到框架服务定义时非空 */
    frameServiceId?: number;
    frameServiceName?: string;
    catalog?: string;
    /** 该 release 是否已经登记过，重扫时用于默认不重复勾选 */
    registered?: boolean;
    /** 来源类型 HELM=Helm release / CR=operator 自定义资源 */
    sourceKind?: string;
    /** CR 的 K8s Kind，如 DorisDisaggregatedCluster；HELM 来源为 undefined */
    kind?: string;
  }

  /** 已登记但集群里已找不到对应 release 的接管实例 */
  interface MissingTakeoverInstance {
    instanceId: number;
    releaseName: string;
    namespace: string;
    serviceName: string;
  }

  /** 接管扫描结果 */
  interface K8sTakeoverScanResult {
    matched: ScannedRelease[];
    pending: ScannedRelease[];
    /** 重扫对账结果：登记还在、release 已不在 */
    missing?: MissingTakeoverInstance[];
  }

  /** Doris 数据源候选地址 */
  interface DorisDatasourceCandidate {
    serviceName: string;
    namespace: string;
    serviceType: string;
    host?: string;
    port?: number;
    source: 'LOAD_BALANCER' | 'NODE_PORT' | 'CLUSTER_IP';
    reachable: boolean;
    hint?: string;
  }

  /** 单个服务的接管登记结果 */
  interface K8sTakeoverRegisterResult {
    instanceId: number;
    releaseName: string;
    namespace: string;
    metricsJob?: string;
    scraped: boolean;
    /** 角色名到其 job 列表的映射（如 {"fe":[...],"compute":[...]}）；仅 CR 来源且探测到角色 job 时非空 */
    roleJobs?: Record<string, string[]>;
  }
}
