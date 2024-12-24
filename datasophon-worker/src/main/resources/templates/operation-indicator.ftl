server:
  port: ${operationServerPort}

spring:
  datasource:
    dynamic:
      primary: workflow #设置默认的数据源或者数据源组,默认值即为master
      strict: false #严格匹配数据源,默认false. true未匹配到指定数据源时抛异常,false使用默认数据源
      datasource:
        workflow:
          username: ${operationDbWorkflowUsername}
          password: ${operationDbWorkflowPassword}
          driver-class-name: ${operationDbWorkflowDriverClassName}
          url: ${operationDbWorkflowUrl}
        doris:
          username: ${operationDbDorisUsername}
          password: ${operationDbDorisPassword}
          driver-class-name: ${operationDbDorisDriverClassName}
          url: ${operationDbDorisUrl}
      druid:
        connect-timeout: 6000000
        query-timeout: 6000000
        max-evictable-idle-time-millis: 6000000
        time-between-eviction-runs-millis: 6000000
        keep-alive-between-time-millis: 7000000
        initial-size: 50
        max-active: 2000
        min-idle: 50
        max-wait: 120000

prometheus:
  url: ${operationPrometheusUrl}


ds: 
  baseUrl: ${operationDsBaseUrl}
  interfaceCollectCommand: java -jar /opt/interface-collector/interface-collector.jar PARAM01
  kettleCommand: java -jar /opt/kettle/chinaunicom-medical-business-dc-v2-adapter-kettle-1.0-SNAPSHOT.jar -f PARAM01
  flinkxCommand: /opt/flinkx/bin/flinkx -mode local -job job.json -pluginRoot /opt/flinkx/syncplugins -flinkconf /opt/flinkx/flinkconf
  usqlCommand: spark-submit --class com.chinaunicom.usql.core.USQLEngine PARAM01 usql-standalone/target/usql-1.0.0-spark-2.4.7-2.11-jar-with-dependencies.jar -j hudi2console.dsl
  logDir: ${operationDsLogDir}
  ownBasePath: ${operationDsOwnBasePath}
  logAccess:
    - ${operationDsLogAccessUrl1}
    - ${operationDsLogAccessUrl2}
    - ${operationDsLogAccessUrl3}
    - ${operationDsLogAccessUrl4}
    - ${operationDsLogAccessUrl5}
    - ${operationDsLogAccessUrl6}
    - ${operationDsLogAccessUrl7}
    - ${operationDsLogAccessUrl8}
    - ${operationDsLogAccessUrl9}
    - ${operationDsLogAccessUrl10}
    - ${operationDsLogAccessUrl11}


# hsb.apisix-url: http://10.91.95.33:9080
hsb.apisix-url: ${operationHsbApisixUrl}
# hsb.apisix-url: http://10.91.95.36:32000
  

api-six:
  base-url: ${operationApiSixBaseUrl}
  xApiKey: ${operationApiSixXApiKey}
  # base-url: http://10.91.95.33:9080
  # xApiKey: edd1c9f034335f136f87ad84b625c8f1
  # base-url: http://10.91.95.36:32001
  # xApiKey: edd1c9f034335f136f87ad84b625c8f123
  # base-url: http://10.91.22.8:9080
  # xApiKey: edd1c9f034335f136f87ad84b625c8f1
  allowOrigins: ${operationApiSixAllowOrigins}
  httpLoggerEndpoint: ${operationApiSixHttpLoggerEndpoint}
  limitCountRedisHost: ${operationApiSixLimitCountRedisHost}
  limitCountRedisPort: ${operationApiSixLimitCountRedisPort}
  limitCountRedisPassword: ${operationApiSixLimitCountRedisPassword}
  limitCountRedisDatabase: ${operationApiSixLimitCountRedisDatabase}
  kafkaLoggerBrokerList:
    - ${operationApiSixKafkaLoggerBrokerList1}
  kafkaLoggerKafkaTopic: ${operationApiSixKafkaLoggerKafkaTopic}
  kafkaLoggerKey: ${operationApiSixKafkaLoggerKey}
  kafkaLoggerDisable: ${operationApiSixKafkaLoggerDisable}
hsb-node:
  address:
    - ${operationHsbNodeAddress}

pagehelper:
  helper-dialect: mysql
  reasonable: true
  support-methods-arguments: true
  params: count=countSql

dc:
  kerberos:
    tempdir: ${operationDcKerberosTempdir}

qc:
  executeResult: ${operationQcExecuteResult}
  imgServer.url: ${operationQcImgServerUrl}

metadata.syncTask.callback: ${operationMetadataSyncTaskCallback}
metadata.collector.callback: ${operationMetadataCollectorCallback}
metadata.permissions.executePermissionsJudge: ${operationMetadataPermissionsExecutePermissionsJudge}
metadata.blood.receive: ${operationMetadataBloodReceive}

module:
  batchsyncdata:
    tempdir: ${operationModuleBatchsyncdataTempdir}
    
hsb.msg.topicPrefix: ${operationHsbMsgTopicPrefix}



com:
  chinaunicom:
  medical:
    comp:
      etcd:
        enabled: true
        location: ${operationComChinaunicomMedicalCompEtcLocation}
      es:
        enabled: true
        nodes: ${operationComChinaunicomMedicalCompEsNodes}
  
ustream:
  user: ${operationUstreamUser}
  yarn-rm-address: ${operationUstreamYarnRmAddress}
  agent-yarn-rm-address: ${operationUstreamAgentYarnRmAddress}
  flink:
    home: ${operationUstreamFlinkHome}
    history-rest-address: ${operationUstreamFlinkHistoryRestAddress}
  gateway:
    home: ${operationUstreamGatewayHome}
    jarname: ${operationUstreamGatewayJarname}
    mainclass: ${operationUstreamGatewayMainclass}

jinliu:
  baseUrl: ${operationJinliuBaseUrl}
  kafkaUrl: ${operationJinliuKafkaUrl}
  kafkaAccount: ${operationJinliuKafkaAccount}
  kafkaPassword: ${operationJinliuKafkaPassword} 


log: 
  level: info
  max-file-size: 100MB
  max-history: 15
  path: .
logging:
  config: classpath:logback-medical.xml
  level:
    root: info

link:
  isEnable: 1
  isCollect: 0
  noCollectRequestBodyInterfacePaths:
    - /xx
  noCollectResponseBodyInterfacePaths:
    - /qc/v1/qcReport/record/content
    - /qc/v1/qcTaskInstanceStandalone/downloadDataQualityCenterReport
    - /qc/v1/qcTaskInstanceStandalone/downloadQualityDetails
    - /qc/v1/qcTaskInstanceCombine/downloadDataQualityCenterReport
    - /qc/v1/qcTaskInstanceCombine/downloadQualityDetails
  logPaths:
    - /xx
  releaseUris:
    - /actuator/prometheus

standard:
  isfileSize: 200
chinaunicom.medical.capacity.workflow.server: workflow-zhcdcapp

workweixin:
  corpid: ${operationWorkweixinCorpid}
  defaultApp: ${operationWorkweixinDefaultApp}
  url: ${operationWorkweixinUrl}
  appConfig:
    digit:
      appId: ${operationWorkweixinAppConfigDigitAppId}
      appSecret: ${operationWorkweixinAppConfigDigitAppSecret}

operationbrain:
  scheduled:
    warningTask:
      enable: true

mybatis-plus:
  mapper-locations: classpath*:com/gitee/sunchenbin/mybatis/actable/mapping/*/*.xml,classpath:mappers/*Mapper.xml
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

minio:
  accessKey: ${operationMinioAccessKey}
  secretKey: ${operationMinioSecretKey}
  bucketName: ${operationMinioBucketName}
  endpoint: ${operationMinioEndpoint}
  outsideEndpoint: ${operationMinioOutsideEndpoint}

sso:
  accessTokenKey: ${operationSsoAccessTokenKey}
  domain: ${operationSsoDomain}
  rolesystem: ${operationSsoRolesystem}
  jwt:
    tokenExpiration: ${operationSsoJwtTokenExpiration}
    tokenSecret: ${operationSsoJwtTokenSecret}