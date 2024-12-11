##默认配置文件，非必要情况请勿修改！！！
server {
    listen ${nginxWebPort};
    server_name _;
    underscores_in_headers on;

##前端转发配置
    location ~ ^/.*-view/ {
        root ${nginxWebappPath};
        index index.html index.htm;

    }

}