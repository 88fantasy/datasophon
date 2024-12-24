server.port: 5223

spring:
  datasource:
    dynamic:
      primary: doris
      datasource:
        system:
          username: ${researchDbSystemUsername}
          password: ${researchDbSystemPassword}
          driver-class-name: ${researchDbSystemDriverClassName}
          url: ${researchDbSystemUrl}
        doris:
          username: ${researchDbDorisUsername}
          password: ${researchDbDorisPassword}
          driver-class-name: ${researchDbDorisDriverClassName}
          url: ${researchDbDorisUrl}


logging:
#   config: classpath:logback-spring.xml
  level:
    root: info
log:
  level: info

oa_admin:
  url: ${researchOaAdminUrl}

minio:
  enabled: true
  bucket: public
  publicReadDir: public
  proxyUrl: ${researchMinioHost}

retrieval:
  #topOrgNodeId: 1595006145888526338
  topOrgNodeId: 1
  localDir: tmpFile/
  invalidDay: 30
  dataSetCount: 6797
  indexCount: 115
  inspectFeeNames: 血常规
  adminRoleId: 1764485116517728257
  forbidPatientUsernames: 
  excludeOrg: YD,ZQ

  sparkExport: false
  dsUrl: ${researchRetrievalDsUrl}
  dsProjectName: ${researchRetrievalDsProjectName}
  dsUsername: ${researchRetrievalDsUsername}
  dsPassword: ${researchRetrievalDsPassword}

  visitTypeFieldId: 56
  inVisitNoFieldId: 766
  outOrgCodeFieldId: 50
  inOrgCodeFieldId: 2053
  outDeptCodeFieldId: 49
  admDeptCodeFieldId: 836
  disDeptCodeFieldId: 845
  visitTimeFieldId: 67
  admTimeFieldId: 835
  disTimeFieldId: 844
  outDiagnosisCodeFieldId: 44
  outDiagnosisNameFieldId: 45
  inDiagnosisCodeFieldId: 1035
  inDiagnosisNameFieldId: 1037
  inspectResultNumFieldId: 746
  scheduled:
    historyClean:
      enable: true
      cron: 0 0 2 ? * SUN
      saveNum: 20
    exportTaskInvalid:
      enable: true
      cron: 0 0 2 * * ?

  aiChatMode: test
  beginDate: '2024-01-08'
  endDate: '2024-09-08'

retrieval.scheduled.tableAndFieldInit.enable: true
retrieval.scheduled.tableAndFieldInit.schemaName: chinaunicom_medical_zssy_web
retrieval.scheduled.tableAndFieldInit.tablePrefix: ads_srrs_
retrieval.scheduled.tableAndFieldInit.batchSize: 50
retrieval.scheduled.tableAndFieldInit.rootCatalogName: 未归类指标集

#切换新老门户开关，默认portal新门户
chinaunicom.medical.capacity.user.server: ${researchCapacityUserServerName}