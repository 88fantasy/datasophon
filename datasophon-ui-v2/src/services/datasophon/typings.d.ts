declare namespace DATASOPHON {
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

  interface UserInfo {
    id: number;
    username: string;
  }
}
