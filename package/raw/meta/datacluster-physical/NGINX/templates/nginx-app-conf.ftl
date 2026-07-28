<#list nginxAppConf as item>
# ${item.appName}
location ${item.prefixUrl} {
    <#list item.directives?split(";") as directive>
        ${directive} <#sep>;
    </#list>
    proxy_pass ${item.proxyRewrite};
}
</#list>