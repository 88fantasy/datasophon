[server]
root_url = http://0.0.0.0:3000/ddh/grafana/${clusterId}
serve_from_sub_path = true

[security]
allow_embedding = true

[live]
allowed_origins = *

[auth.anonymous]
# enable anonymous access
enabled = true

# specify organization name that should be used for unauthenticated users
org_name = Main Org.

# specify role for unauthenticated users
org_role = Viewer