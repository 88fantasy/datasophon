server {
    listen ${nginxStatusPort};
    listen [::]:${nginxStatusPort};
    server_name _;

    location /nginx_status {
        stub_status;
        allow 127.0.0.1;
    }

}