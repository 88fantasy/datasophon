#!/usr/bin/env bash
set -euo pipefail

tag="${1:-latest}"
shift 2>/dev/null || true

proxy=""
arch=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --proxy)
      proxy="$2"
      shift 2
      ;;
    --arch)
      arch="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

if [ -n "$proxy" ]; then
  export https_proxy="$proxy"
  export http_proxy="$proxy"
  echo "use proxy: $proxy."
fi


image_name="vos/datasophon-k8s-agent"

if [ "$arch" = "all" ]; then
  platform="linux/amd64,linux/arm64"
elif [ -n "$arch" ]; then
  platform="linux/${arch}"
fi

if [ "$arch" = "all" ]; then
  BUILDER_NAME="datasophon-build"

  builders=$(docker buildx ls)
  if echo "${builders}" | grep -q "${BUILDER_NAME}"; then
    echo "BuildKit builder '${BUILDER_NAME}' already exists."
  else
    echo "BuildKit builder '${BUILDER_NAME}' does not exist."
    docker buildx create --name ${BUILDER_NAME} --platform linux/amd64,linux/arm64 --use --bootstrap
  fi

  docker buildx build \
   --builder ${BUILDER_NAME} \
   --platform=${platform} \
   --progress plain \
   --output type=oci,dest=./datasophon-k8s-agent-${tag}-image-all.tar \
   -t ${image_name}:${tag} .
elif [ -n "$arch" ]; then
  docker build \
   --platform=${platform} \
   --progress plain \
   --output type=docker,dest=./datasophon-k8s-agent-${tag}-image-$arch.tar \
   -t ${image_name}:${tag} .
else
  docker build \
   --progress plain \
   --output type=docker,dest=./datasophon-k8s-agent-${tag}-image-default.tar \
   -t ${image_name}:${tag} .
fi