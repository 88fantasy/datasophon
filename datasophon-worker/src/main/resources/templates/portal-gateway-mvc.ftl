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
              - RewritePath=<#rt>${route.rewriteReg}<#lt>, <#rt>${route.replaceReg}<#lt>
</#list>