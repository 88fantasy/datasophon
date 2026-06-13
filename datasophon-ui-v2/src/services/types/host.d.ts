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
}
