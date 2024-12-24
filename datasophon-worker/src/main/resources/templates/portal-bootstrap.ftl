profiles:
  active: ${portalProfilesActive}

env:
  nacos:
    host: ${portalNacosHost}
    port: ${portalNacosPort}
    username: ${portalNacosUsername}
    password: ${portalNacosPassword}


portal:
  login:
    oauth20-font-end-redirect: "${portalDomain}/gateway-view/#/login"
oauth20:
  type:
    DINGDING:
      redirect-uri: ${portalDomain}/gateway/portal/oauth20/callback/dingding
  proxy:
    DINGDING:
      hostname: ${dingdingProxyHost}
      port: ${dingdingProxyPort}


oss:
  minio:
    outside-url: ${minioOutsideUrl}