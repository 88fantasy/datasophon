#!/bin/bash
<#if itemList?? && itemList?has_content>
    ${nginxBasePath}/configure --prefix=${nginxBasePath}/nginx \
    <#list itemList as item>
        <#list item.value?split(",") as module>
            ${module} \
        </#list>
    </#list>
</#if>

