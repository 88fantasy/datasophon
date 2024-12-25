spring:
  cloud:
    gateway:
      mvc:
        enabled: true
        http-client:
          type: jdk
        routes:
<#list portalRoutes as route>
          - id: ${route.id}
            uri: ${route.uri}
            predicates:
              - Path=${route.predicateUrl}
            filters:
              - RewritePath=${route.rewriteReg}, ${route.replaceReg}
</#list>
