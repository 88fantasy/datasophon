declare namespace DATASOPHON {
  /** @deprecated 旧 DAO 镜像，迁移完成后删除，暂保留供未迁移文件使用 */
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

  /** v2 主机详情响应体。不含 sshPassword / sshUser / managed 敏感字段。 */
  interface HostResponse {
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
    createTime?: string;
    clusterId?: number;
    /** 主机状态数字：1=正常运行，2=掉线，3=存在告警 */
    hostState?: number;
    cpuArchitecture?: string;
    nodeLabel?: string;
    serviceRoleNum?: number;
    sshPort?: number;
    // 不含 sshPassword、sshUser、managed
  }

  /** v2 主机分页响应体。 */
  interface HostPageResponse {
    records: HostResponse[];
    total: number;
  }

  /** v2 主机角色响应体。 */
  interface HostRoleResponse {
    id: number;
    serviceRoleName: string;
    hostname?: string;
    /** 角色状态码：1=运行中，2=停止，3=存在告警，4=退役中，5=已退役 */
    serviceRoleStateCode?: number;
    serviceId?: number;
    clusterId?: number;
  }
}
