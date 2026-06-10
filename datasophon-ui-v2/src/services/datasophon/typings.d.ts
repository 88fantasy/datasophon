declare namespace DATASOPHON {
  interface ApiResponse<T = any> {
    success: boolean;
    data: T;
    errorCode?: number;
    errorMessage?: string;
    showType?: string;
  }
  type ClusterArchType = 'physical' | 'k8s';

  type ClusterState =
    | '正在运行'
    | '停止'
    | '告警'
    | '安装中'
    | '停止中'
    | '启动中'
    | '未知';

  interface ClusterManager {
    id: number;
    username: string;
  }

  interface ClusterInfo {
    id: number;
    clusterName: string;
    clusterCode: string;
    clusterFrame: string;
    frameVersion?: string;
    frameId?: number;
    clusterState?: ClusterState;
    clusterStateCode?: number;
    archType?: ClusterArchType;
    clusterManagerList?: ClusterManager[];
  }

  interface FrameInfo {
    id: number;
    frameName: string;
    frameCode: string;
    frameVersion: string;
  }

  interface HostInfo {
    id: number;
    hostname: string;
    ip: string;
    rack?: string;
    coreNum?: number;
    totalMem?: number;
    totalDisk?: number;
    usedMem?: number;
    usedDisk?: number;
    averageLoad?: string;
    checkTime?: string;
    clusterId?: number;
    hostState: number;
    cpuArchitecture?: string;
    nodeLabel?: string;
    serviceRoleNum?: number;
    createTime?: string;
    sshPort?: number;
    sshUser?: string;
  }

  interface UserInfo {
    id: number;
    username: string;
    email?: string;
    phone?: string;
    password?: string;
    createTime?: string;
    userType?: number;
  }

  interface ServiceInstanceInfo {
    id: number;
    clusterId: number;
    serviceName: string;
    label: string;
    serviceState: string;
    serviceStateCode: number;
    needRestart: boolean;
    frameServiceId: number;
    sortNum: number;
    dashboardUrl?: string;
    alertNum: number;
    catalog: string;
    createTime?: string;
    updateTime?: string;
  }

  interface ServiceRoleInstanceInfo {
    id: number;
    serviceId: number;
    serviceRoleName: string;
    hostname: string;
    roleGroupId: number;
    roleGroupName?: string;
    serviceRoleState: string;
    serviceRoleStateCode: number;
    clusterId: number;
    createTime?: string;
  }

  interface WebuiInfo {
    name: string;
    webUrl: string;
  }

  interface FrameServiceItem {
    id: number;
    frameId: number;
    frameCode: string;
    serviceName: string;
    serviceVersion: string;
    serviceDesc?: string;
  }

  interface FrameK8sServiceItem {
    id: number;
    frameId: number;
    serviceName: string;
    serviceVersion: string;
    serviceDesc?: string;
    supportArtifacts?: string[];
  }

  interface FrameWithServices extends FrameInfo {
    frameServiceList?: FrameServiceItem[];
    frameK8sServiceList?: FrameK8sServiceItem[];
  }

  // ── K8s 集群链路类型 ──────────────────────────────────────────────────────

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

  /**
   * 服务配置参数项，对应后端 ServiceConfig 模型。
   * type 决定前端渲染控件类型：
   *   input / password / slider / switch / select / multipleSelect /
   *   multiple / multipleWithKey / multipleWithMap
   */
  interface ConfigField {
    name: string;
    label: string;
    value: any;
    defaultValue?: any;
    description?: string;
    required: boolean;
    enabled: boolean;
    hidden?: boolean;
    type: string;
    configType?: string;
    minValue?: number;
    maxValue?: number;
    unit?: string;
    selectValue?: string[];
    key?: string;
    originalName?: string;
    separator?: string;
  }
}
