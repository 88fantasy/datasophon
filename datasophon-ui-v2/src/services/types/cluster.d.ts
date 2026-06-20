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

  // ── v2 DTO Request / Response（cluster 试点）────────────────────────────────

  /** POST /v2/cluster 请求体 */
  interface CreateClusterRequest {
    clusterName: string;
    clusterCode: string;
    frameId: number;
    archType: ClusterArchType;
  }

  /** PUT /v2/cluster/{id} 请求体（只修改名称和代号） */
  interface UpdateClusterRequest {
    clusterName: string;
    clusterCode: string;
  }

  /** GET /v2/cluster/list、POST /v2/cluster 响应体 */
  interface ClusterResponse {
    id: number;
    clusterName: string;
    clusterCode: string;
    clusterFrame?: string;
    frameVersion?: string;
    frameId?: number;
    clusterState?: ClusterState;
    clusterStateCode?: number;
    archType?: ClusterArchType;
    clusterManagerList?: ClusterManager[];
  }

  /** K8s 集群连接配置 */
  type K8sConnectType = 'config_file' | 'token' | 'password';

  interface K8sConfig {
    clusterId?: number;
    type: K8sConnectType;
    serverHost?: string;
    serverCert?: string;
    token?: string;
    username?: string;
    password?: string;
    kubeConfig?: string;
  }

  interface K8sTestResult {
    success: boolean;
    info?: string;
  }

  /** @deprecated 旧 DAO 镜像，迁移完成后删除，暂保留供未迁移文件使用 */
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
}
