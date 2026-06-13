declare namespace DATASOPHON {
  /** 部署清单上下文（步骤 1 产出，后续步骤复用） */
  interface ManifestContext {
    deployFileId: number;
    contentDecodePasswd: string;
  }

  /** 框架服务（FrameServiceEntity），list-newest 返回 */
  interface FrameService {
    id: number;
    serviceName: string;
    label?: string;
    serviceVersion?: string;
    serviceDesc?: string;
    frameCode?: string;
    /** 是否已安装到集群 */
    installed?: boolean;
    /** 部署清单中是否勾选 */
    selected?: boolean;
  }

  /**
   * 框架服务角色（FrameServiceRoleEntity）。
   * serviceRoleType: master | worker | client
   * cardinality: "1" 单选 | "1+"/"N+" 多选
   */
  interface FrameServiceRole {
    id: number;
    serviceId: number;
    serviceRoleName: string;
    serviceRoleType: string;
    cardinality?: string;
    /** 部署清单回填的主机列表 */
    hosts?: string[];
  }

  /** 服务角色与主机映射（提交给 role-host-mapping） */
  interface RoleHostMapping {
    serviceRole: string;
    hosts: string[];
  }
}
