##默认配置文件，切勿修改！！！
server {
    listen ${nginxServerPort};
    server_name _;
    underscores_in_headers on;      #启用请求头参数值包含下划线模式

##通用请求转发配置，转至20011端口负责处理（包含minio请求、统一的前端请求、大屏请求、组件管理端访问请求等通用的配置）
    location / {
        proxy_pass http://127.0.0.1:${nginxWebPort}/;
    }

##后端应用配置，采用包含的模式实现模块化管理、叠加效果，这部分配置文件只需编写location层级即可，按照产品划分文件；
    include ${nginxInstallPath}/conf/confs/inside_of_http_confs/app-confs/*.conf;
}