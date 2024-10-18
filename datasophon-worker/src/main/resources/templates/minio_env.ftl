# version 8.4.3
export MINIO_ROOT_USER=${accessKey}
export MINIO_ROOT_PASSWORD=${secretKey}
export MINIO_PROMETHEUS_AUTH_TYPE=public
# MINIO_VOLUMES sets the storage volume or path to use for the MinIO server.

export MINIO_VOLUMES="${dataPaths}"

# MINIO_OPTS sets any additional commandline options to pass to the MinIO server.
# For example, `--console-address :9001` sets the MinIO Console listen port
export MINIO_OPTS="--console-address :${consolePort}"
