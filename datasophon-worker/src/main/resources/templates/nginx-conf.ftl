load_module modules/ngx_stream_module.so;

#user  nobody;
#user  worker;
worker_processes  4;


events {
    worker_connections  1024;
}

include ${nginxOutSideConfIncludePath};

http {
    include       mime.types;
    default_type  application/octet-stream;
    charset  utf-8;
    server_tokens off;

    log_format  access_json  '{"@timestamp":"$time_iso8601",'
        '"@version":"1",'
            '"client":"$remote_addr",'
            '"url":"$uri",'
            '"status":"$status",'
            '"domain":"$host",'
            '"host":"$server_addr",'
            '"size":"$body_bytes_sent",'
            '"responsentime":"$request_time",'
            '"referer":"$http_referer",'
            '"useragent":"$http_user_agent",'
            '"upstreampstatus":"$upstream_status",'
            '"upstreamaddr":"$upstream_addr",'
            '"upstreamresponsetime":"$upstream_response_time"'
            '}';

        access_log  logs/access/access.log  access_json;
        #limit_rate_after 5m;
        #limit_rate 256k;
        keepalive_timeout 300;
        send_timeout 300;
        sendfile        on;
        client_max_body_size 20M;
        client_body_buffer_size 128k;
        fastcgi_connect_timeout 1800;
        fastcgi_send_timeout    1800;
        fastcgi_read_timeout    1800;
        fastcgi_buffer_size 64k;
        fastcgi_buffers 8 128k;
        fastcgi_busy_buffers_size 128k;
        fastcgi_temp_file_write_size 128k;
        proxy_connect_timeout   1800;
        proxy_send_timeout      1800;
        proxy_read_timeout      1800;
        gzip on;
        gzip_min_length 1k;
        gzip_buffers 4 16k;
        gzip_http_version 1.1;
        gzip_comp_level 3;
        gzip_types text/plain application/json application/javascript application/x-javascript application/css application/xml application/xml+rss text/javascript application/x-httpd-php image/jpeg image/gif image/png image/x-ms-bmp;
        gzip_vary off;
        include ${nginxInSideConfIncludePath};
}