server.port: 5225

spring:
  datasource:
    dynamic:
      primary: doris
      datasource:
        system:
          username: ${researchDbSystemUsername}
          password: ${researchDbSystemPassword}
          driver-class-name: ${researchDbSystemDriverClassName}
          url: ${researchDbSystemDriverClassName}
        doris:
          username: ${researchDbDorisUsername}
          password: ${researchDbDorisPassword}
          driver-class-name: ${researchDbDorisDriverClassName}
          url: ${researchDbDorisUrl}

logging:
  #   config: classpath:logback-spring.xml
  level:
    root: info
    com.chinaunicom.medical.app.research.util: debug
log:
  level: info

oa_admin:
  url: ${researchOaAdminUrl}

minio:
  enabled: true
  bucket: retrieval
  publicReadDir: public
  proxyUrl: ${researchMinioHost}

research:
  topOrgNodeId: 1595006145888526338
  overviewDesc: 依托科研数据治理建设科研数据应用，包括优势学科专病数据库，科研队列管理中心和数据检索。通过专病库的建设，为科研人员提供更为丰富的研究数据，进一步挖掘医院数据的资产价值；通过科研队列管理中心，更好地跟踪和管理队列患者的治疗全流程数据，辅助科研人员进行前瞻性队列研究；同时，数据检索的升级将为医院的各个院区提供更强大的数据搜索和分析能力，帮助医务人员更快地获取所需的医疗信息。
  models:
    - code: Qwen-14B-Chat
      name: 通义千问14B
    - code: Qwen-14B-Chat-Int4
      name: 通义千问14B-Int4
    - code: Qwen-7B-Chat
      name: 通义千问7B
    - code: Qwen-72B-Chat-Int4
      name: 通义千问72B
  llmApi: ${researchLlmApi}
  llmModel: string
  llmDoSample: true
  llmTemperature: 0.51
  llmTopp: 0.7
  llmn: 1
  llmMaxTokens: 1000
  synonymExtractModel: Qwen-14B-Chat-Int4
  synonymExtractPrompts:
    - |
      你是一个文本信息抽取模型，从输入文本中提取与{indexName}症状描述相关的语句。
      请按顺序输出，只需要输出原句，不需要其他文字解释。
      如果输入文本中没有相关的语句，请输出无相关语句。
      输入内容如下：{text} 请输出你的回答：
    - |
      你是一个文本信息抽取模型，从输入文本中提取{indexName}的同义词和近义词。
      请按顺序输出，只需要输出原词，不需要其他文字解释。
      如果输入文本中没有同义词或近义词，请输出无结果。
      请将你的响应以json数组格式输出。
      输入内容如下：{lastAnswer} 请输出你的回答：
  mode: test
  scheduled:
    indexDataSourceNum:
      enable: true
      cron: 0 0 5 * * ?
    indexExtractQueue:
      enable: true
    indexProduceQueue:
      enable: true
    structureTaskDetailQueue:
      enable: true
    structureTaskStatus:
      enable: true

structure:
  mode: test
  executeCoreSize: 1
  models:
    - code: subj_complaint
      name: 主诉结构化模型
      version: 1.0
      type: 1
      url: ${researchStructuredMedicalChiefComplaintUrl}
      labels: 症状,时间,诱因
      description: 主诉结构化
      tableFields:
        - name: 门急诊病历-主诉
          tableCode: ads_srrs_pc_emr_summary_health_inc_daily
          fieldCode: subj_complaint
        - name: 入院记录-主诉
          tableCode: ads_srrs_pc_adm_record_inc_daily
          fieldCode: complained
    - code: past_disease_history
      name: 既往史结构化模型
      version: 1.0
      type: 3
      url: ${researchStructuredMedicalPastHistoryUrl}
      labels: 既往疾病,既往症状,既往用药,既往手术,输血史,过敏史
      description: 既往史结构化
      tableFields:
        - name: 门急诊病历-既往史
          tableCode: ads_srrs_pc_emr_summary_health_inc_daily
          fieldCode: past_disease_history
        - name: 入院记录-既往史
          tableCode: ads_srrs_pc_adm_record_inc_daily
          fieldCode: disease_history
    - code: personal_history
      name: 个人史结构化模型
      version: 1.0
      type: 4
      url: ${researchStructuredMedicalPersonalHistoryUrl}
      labels: 是否吸烟,烟龄(年),吸烟量(支/日),是否戒烟,戒烟年限,复吸年限,是否饮酒,饮酒年限,是否戒酒,戒酒年限,复饮年限
      description: 个人史结构化
      tableFields:
        - name: 门急诊病历-个人史
          tableCode: ads_srrs_pc_emr_summary_health_inc_daily
          fieldCode: personal_history
        - name: 入院记录-个人史
          tableCode: ads_srrs_pc_adm_record_inc_daily
          fieldCode: personal_history
    - code: family_history
      name: 家族史结构化模型
      version: 1.0
      type: 5
      url: ${researchStructuredMedicalFamilyHistoryUrl}
      labels: 祖父,祖母,外祖父,外祖母,父亲,母亲,子,女,兄,弟,姐,妹
      description: 家族史结构化
      tableFields:
        - name: 门急诊病历-家族史
          tableCode: ads_srrs_pc_emr_summary_health_inc_daily
          fieldCode: family_histor
        - name: 入院记录-家族史
          tableCode: ads_srrs_pc_adm_record_inc_daily
          fieldCode: family_history

project:
  executeCoreSize: 1
  maxQueueSize: 2000
  llmApi: ${researchProjectLlmApi}
  llmModel: /data/lzl/model_base/Qwen2/Qwen2-72B-Instruct-GPTQ-Int4
  llmDoSample: true
  llmTemperature: 0.51
  llmTopp: 0.7
  llmn: 1
  llmMaxTokens: 1000
  prompt:
  mode: test