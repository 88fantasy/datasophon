declare namespace DATASOPHON {
  /** @deprecated 使用 AlertGroupResponse */
  interface AlertGroup {
    id?: number;
    alertGroupName: string;
    alertGroupCategory: string;
    alertQuotaNum?: number;
    clusterId?: number;
    createTime?: string;
  }

  /** @deprecated 使用 AlertQuotaResponse */
  interface AlertQuota {
    id?: number;
    alertQuotaName: string;
    alertExpr: string;
    compareMethod: string;
    alertThreshold: number;
    alertLevel: string;
    alertGroupId: number;
    alertGroupName?: string;
    serviceRoleName: string;
    noticeGroupId: number;
    alertTactic: number;
    intervalDuration: number;
    triggerDuration: number;
    alertAdvice: string;
    quotaState?: string;
    quotaStateCode?: number;
  }

  /** 告警组响应体（v2），对应后端 AlertGroupResponse */
  interface AlertGroupResponse {
    id?: number;
    alertGroupName: string;
    alertGroupCategory: string;
    alertQuotaNum?: number;
    clusterId?: number;
    createTime?: string;
  }

  /** 告警组分页响应体（v2） */
  interface AlertGroupPageResponse {
    totalList: AlertGroupResponse[];
    totalCount: number;
  }

  /** 告警组类别响应体（v2），对应后端 AlertCategoryResponse */
  interface AlertCategoryResponse {
    serviceName: string;
  }

  /** 新建告警组请求体 */
  interface SaveAlertGroupRequest {
    alertGroupName: string;
    alertGroupCategory: string;
  }

  /** 告警指标响应体（v2），对应后端 AlertQuotaResponse */
  interface AlertQuotaResponse {
    id?: number;
    alertQuotaName: string;
    alertExpr: string;
    compareMethod: string;
    alertThreshold: number;
    alertLevel: string;
    alertGroupId: number;
    alertGroupName?: string;
    serviceRoleName: string;
    noticeGroupId: number;
    alertTactic: number;
    intervalDuration: number;
    triggerDuration: number;
    alertAdvice: string;
    quotaState?: string;
    quotaStateCode?: number;
  }

  /** 告警指标分页响应体（v2） */
  interface AlertQuotaPageResponse {
    totalList: AlertQuotaResponse[];
    totalCount: number;
  }

  /** 新建告警指标请求体 */
  interface SaveAlertQuotaRequest {
    alertQuotaName: string;
    alertExpr?: string;
    compareMethod?: string;
    alertThreshold?: number;
    alertLevel?: string;
    alertGroupId?: number;
    serviceRoleName?: string;
    noticeGroupId?: number;
    alertTactic?: number;
    intervalDuration?: number;
    triggerDuration?: number;
    alertAdvice?: string;
  }

  /** 修改告警指标请求体 */
  interface UpdateAlertQuotaRequest extends SaveAlertQuotaRequest {
    id: number;
  }
}
