declare namespace DATASOPHON {
  /** APISIX 角色实例节点视图 */
  interface ApisixGatewayRole {
    hostname: string;
    state: string;
  }

  /** GET /v2/.../apisix/gateway 响应体 */
  interface ApisixGatewayResponse {
    /** 用户可编辑段（upstreams/routes/global_rules）；未保存过时为向导参数拼出的初始值 */
    gatewayYaml: string;
    /** 模板固定输出的托管段（plugin_metadata + #END），供拼「最终 apisix.yaml」只读预览 */
    managedSuffix: string;
    roles: ApisixGatewayRole[];
  }

  /** 单节点下发结果 */
  interface ApisixGatewayPushResult {
    hostname: string;
    success: boolean;
    message?: string;
  }
}
