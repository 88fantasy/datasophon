declare namespace DATASOPHON {
  /** 集群总览看板聚合响应，对应后端 ClusterDashboardResponse */
  interface ClusterDashboardResponse {
    stats: ClusterDashboardStats;
    alertTrend: ClusterDashboardAlertTrendPoint[];
    serviceHealth: ClusterDashboardServiceHealth[];
    profile: ClusterDashboardProfile;
  }

  interface ClusterDashboardStats {
    hostTotal: number;
    /** 今日新增主机数 - 昨日新增主机数，可为负 */
    hostDelta: number;
    serviceTotal: number;
    /** 今日新增服务数 - 昨日新增服务数，可为负 */
    serviceDelta: number;
    /** 未处理告警总数 */
    alertTotal: number;
    /** 今日新增告警数 - 昨日新增告警数，可为负 */
    alertDelta: number;
    criticalAlertTotal: number;
    criticalAlertDelta: number;
  }

  /** 近 7 天告警趋势的一天，yyyy-MM-dd，无告警的日期已补零 */
  interface ClusterDashboardAlertTrendPoint {
    day: string;
    warning: number;
    exception: number;
  }

  /** 按健康度升序排列的问题服务 TOP5 */
  interface ClusterDashboardServiceHealth {
    serviceName: string;
    label: string;
    /** 运行角色数/总角色数*100；未装角色时为 null */
    healthPercent: number | null;
    runningRoles: number;
    abnormalRoles: number;
    totalRoles: number;
    alertNum: number;
    serviceState: string;
  }

  interface ClusterDashboardProfile {
    clusterName: string | null;
    clusterFrame: string | null;
    frameVersion: string | null;
    clusterState: string | null;
    createTime: string | null;
    nodeCount: number;
    totalCores: number;
    /** GiB */
    totalMemGb: number;
    /** GiB */
    totalDiskGb: number;
    cpuArchitectures: string[];
    osName: string | null;
    kernelVersion: string | null;
    uptimeSeconds: number | null;
  }

  /** 告警历史记录（对应后端 ClusterAlertHistory），用于「最新告警」面板 */
  interface ClusterAlertHistoryRecord {
    id: number;
    alertGroupName: string;
    alertTargetName: string;
    alertInfo: string;
    alertAdvice: string;
    hostname: string;
    /** "warning" | "exception" */
    alertLevel: string;
    isEnabled: number;
    serviceRoleInstanceId: number;
    serviceInstanceId: number;
    createTime: string;
    updateTime: string;
    clusterId: number;
  }
}
