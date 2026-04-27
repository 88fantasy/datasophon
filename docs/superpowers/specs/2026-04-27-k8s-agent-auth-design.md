# K8s Agent 鉴权模式设计文档

## Context
datasophon-k8s-agent 模块当前完全无鉴权，需要引入基于 RSA 签名的 HTTP 请求鉴权机制。

## 设计决策
- 签名算法：RSA-SHA256
- Header 前缀：`x-vos-`
- 健康检查端点：排除鉴权
- 配置来源：common.properties 通过 PropertyUtils 读取
- 常量类放在 datasophon-common 模块，供后续客户端调用复用
- 防重放：仅检查时间戳窗口，不需要 nonce 缓存

## 架构

### 配置项 (common.properties)
```properties
k8s.agent.auth.enabled=false
k8s.agent.auth.public.key=
k8s.agent.auth.replay.window.seconds=300
```

### HTTP Header
- `x-vos-timestamp`: Unix 毫秒时间戳
- `x-vos-nonce`: 随机数（UUID）
- `x-vos-signature`: Base64 编码的 RSA 签名

签名内容：`timestamp + nonce` 的字符串拼接

### 文件结构
```
datasophon-common/src/main/java/com/datasophon/common/utils/
    K8sAgentAuthConstants.java              (新 - 常量类)

datasophon-k8s-agent/src/main/java/com/datasophon/k8sagent/
    auth/
        SignatureVerifier.java              (新 - 签名验证)
        K8sAgentAuthInterceptor.java        (新 - 鉴权拦截器)
    configuration/
        K8sAgentWebConfiguration.java       (新 - Web配置)
```

### 组件说明

#### K8sAgentAuthConstants (common 模块)
- 配置 key 常量
- Header 名称常量
- 默认值

#### SignatureVerifier
- 使用 `java.security` 标准 API (SHA256withRSA)
- 支持 PEM 格式和纯 Base64 格式公钥
- 无状态工具类

#### K8sAgentAuthInterceptor
- 实现 `HandlerInterceptor`
- `preHandle` 逻辑：
  1. 开关未开启 → 放行
  2. 检查三个 header 是否存在
  3. 验证时间戳格式和时效性
  4. 验证签名
  5. 失败返回 401 + JSON 错误

#### K8sAgentWebConfiguration
- 实现 `WebMvcConfigurer`
- 注册拦截器
- 拦截：`/api/v1/**`
- 排除：`/api/v1/health`、`/api/v1/ready`、`/error`
