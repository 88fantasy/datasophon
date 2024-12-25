<#noparse>
portal:
  captcha:
    type: slice
  login:
    auth-type:
      - password
    captcha: true
    remember-me: false
    mfa: false
    mfa-types:
      - mobile
    password:
      max-retry: 5
      lock-time: 3M
      reset-password-when-first-login: true
      change-password-regular: true
      change-password-duration: 31d
      password-invalid-warning-duration: 3d
    concurrent:
      account: false
    oauth:
      dingding:
        action-when-user-absent: reject
        login-ways:
          - third_platform
          - qr_code
      yuezhengyi:
        action-when-user-absent: reject
        login-ways:
          - third_platform


############## Sa-Token 配置 (文档: https://sa-token.cc) ##############
sa-token:
  token-name: token
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-cookie: true
  #  在spring-gateway-mvc的条件下，必须关闭该选项，否则，会触发getParameter操作，最终导致InputStream为空
  is-read-body: false
  timeout: 3600
  #  由AutoRenewRefreshTokenInterceptor控制
  auto-renew: false


oauth20:
  enabled: true
  cache:
    namespace: portal:oauth20
  type:
    DINGDING:
      client-id: dingtsza7raddm8ceybb
      client-secret: ENC(sb2ZpxLW7spxQ6G9McFRBJmVM++ryeUCjVnQnyFzOjQl2UC0eHopc14Yb68sKNgf0lYTq6/YfTqxCLk6ovpJdgbv+N2jU+Ln9nt93k5WrMc=)
  extend:
    YUEZHENGYI:
      client-id:
      client-secret:
      request-class: com.chinaunicom.medical.capacity.session.oauth20.core.request.AuthYuezhengyiRequest
      auth-source-class: com.chinaunicom.medical.capacity.session.oauth20.core.config.AuthDefaultSource
      extra-params:
        corp-id:

</#noparse>

