server.port: 5223

spring:
  datasource:
    dynamic:
      primary: doris
      datasource:
        system:
          username: ${db.system.username:chinaunicom}
          password: ${db.system.password:Un1c0m@Dqc79bf8473#22}
          driver-class-name: ${db.system.driver-class-name:com.mysql.cj.jdbc.Driver}
          url: ${db.system.url:jdbc:mysql://192.168.2.239:33066/chinaunicom_medical_app_zssy?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai}
        doris:
          username: ${db.doris.username:root}
          password: ${db.doris.password:3ght%ed75BGk}
          driver-class-name: ${db.doris.driver-class-name:com.mysql.cj.jdbc.Driver}
          url: ${db.doris.url:jdbc:jdbc:mysql://192.168.2.48:9030/chinaunicom_medical_zssy_web?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai}

logging:
#   config: classpath:logback-spring.xml
  level:
    root: info
log:
  level: info

oa_admin:
  url: ${researchOaAdminUrl:http://10.117.156.94:8380/}

minio:
  enabled: true
  bucket: public
  publicReadDir: public
  proxyUrl: ${minio.url:http://192.168.2.239:6943}

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
  dsUrl: ${researchRetrievalDsUrl:http://10.81.16.199:12345/dolphinscheduler}
  dsProjectName: ${researchRetrievalDsProjectName:retrieval}
  dsUsername: ${researchRetrievalDsUsername:admin}
  dsPassword: ${researchRetrievalDsPassword:dolphinscheduler123}

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