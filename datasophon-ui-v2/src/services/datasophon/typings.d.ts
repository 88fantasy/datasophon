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
}
