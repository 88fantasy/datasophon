#!/bin/bash
${nginxBasePath}/configure --prefix=${nginxInstallPath} \
<#list itemList as item>
    <#list item.value?split(",") as module>
        ${module} \
    </#list>
</#list>