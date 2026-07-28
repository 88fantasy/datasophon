# this file has the highest priority, if val is not blank, then will replace the config

# 数艺数据库配置 必填
datasource.ip=${datartMysqlHost}
datasource.port=${datartMysqlPort}
datasource.database=datart
datasource.username=${datartMysqlUsername}
datasource.password=${datartMysqlPassword}

# ====== 数艺 全局配置 ======
# Web 服务端口 必填
datart.server-port=${datartServerPort}
# Web 服务所绑定的本机网卡地址，一般为内网地址
datart.server-address=${host}

# prometheus监控地址
datart.prometheus-url=

# 数艺内链地址 必填
datart.address=http://${host}
# 数艺外链地址(分享页)
datart.public-address=http://${host}:${datartServerPort}/datart
datart.share=
datart.webdriver-path=
# 注册账户时，是否需要邮件激活
datart.send-mail=false

# 是否允许注册账户
datart.user.register=true
# 注册邮件有效期/小时, 默认48小时
datart.register.expire-hours=
# 邀请邮件有效期/小时, 默认48小时
datart.invite.expire-hours=
# 租户管理模式：platform-平台(默认)，team-团队
datart.tenant-management-mode=platform
# swagger开关
datart.swagger-enabled=

#必填
datart.screenshot.timeout-seconds=
datart.screenshot.webdriver-type=
datart.screenshot.webdriver-path=


datart.workbook-type=

# token校验类型 本地默认:DEFAULT 常用第三方对接:GENERAL 云上方舱:CLOUDS_CABIN
datart.integration.token-type=
# token校验url
datart.integration.verify-url=
# token过期跳转url
datart.integration.token-url=
# 可配置 Cookie / LocalStorage / SessionStorage
datart.integration.token-location=
# token字段名
datart.integration.token-key=
# 加密方式，SM4 / RSA
datart.sso.secret-type=SM4

# MINIO 或 LOCAL(本地)
datart.storage-providers=LOCAL
# Minio服务所在地址
datart.storage-minio.endpoint=
# 存储桶名称
datart.storage-minio.bucket=
# 访问的公钥
datart.storage-minio.access=
# 访问的秘钥
datart.storage-minio.secret=
# 可用区
datart.storage-minio.region=
# 相对路径
datart.storage-minio.path=



# 支持的文件类型
datart.file-suffix=
# 本地数据源配置, 默认与datasource一致
local-exec.driver-class-name=MYSQL
local-exec.datasource.ip=${datartMysqlHost}
local-exec.datasource.port=${datartMysqlPort}
local-exec.datasource.database=datart
local-exec.datasource.username=${datartMysqlUsername}
local-exec.datasource.password=${datartMysqlPassword}

workbench.enable=false
workbench.passwordErrorLimitCount=3
workbench.userAccountLockMinutes=30


# ====== 数艺 数据订阅配置 ======
# 钉钉接口前缀
job.dingtalk.prefix=
# 钉钉应用id
job.dingtalk.agentid=
# 钉钉应用唯一标识key
job.dingtalk.appkey=
# 钉钉应用密钥
job.dingtalk.appsecret=

# 企业微信接口前缀
job.wechart.prefix=
# 企业微信应用id
job.wechart.agentid=
# 企业微信企业ID
job.wechart.corpid=
# 企业微信应用的凭证密钥，注意应用需要是启用状态
job.wechart.secret=

# 飞书接口前缀
job.feishu.prefix=
# 飞书应用id
job.feishu.agentid=
# 飞书应用密钥
job.feishu.appsecret=

# 邮箱服务地址
job.mail.host=
# 端口号
job.mail.port=
# 邮箱地址
job.mail.username=
# 邮箱服务密码
job.mail.password=
# 发送者昵称
job.mail.senderName=