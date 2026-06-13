declare namespace DATASOPHON {
  /** 告警组，对应后端 AlertGroupEntity */
  interface AlertGroup {
    id?: number;
    alertGroupName: string;
    alertGroupCategory: string;
    alertQuotaNum?: number;
    clusterId?: number;
    createTime?: string;
  }

  /** 告警指标，对应后端 ClusterAlertQuota 实体 */
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
}
