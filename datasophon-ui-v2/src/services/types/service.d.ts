declare namespace DATASOPHON {
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

  interface RolePort {
    paramName: string;
    label: string;
    port: number;
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
    /** 该角色实例当前监听的端口(可能多个),查不到时为空数组或未定义。 */
    ports?: RolePort[];
  }

  interface WebuiInfo {
    name: string;
    webUrl: string;
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
