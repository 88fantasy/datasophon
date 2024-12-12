<#list nginxAppConf as item>
#${item.appName}
location ${item.appUrl} {
    <#list item.metaInfo?split(";") as module>
        ${module} <#sep>;
    </#list>
proxy_pass http://${item.appName}:${item.appPort}/;
}
</#list>