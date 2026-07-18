# Valkey 8.1.8 openEuler 离线包构建说明

## 适用范围

本说明对应私有制品 `valkey-8.1.8-openeuler22.03-x86_64.tar.gz`，用于
openEuler 22.03 LTS-SP3/SP4 的 x86_64 节点。

该包由 openEuler 22.03 x86_64 环境原生编译，避免使用 Ubuntu Jammy 预编译包所需的
OpenSSL 3；包内 Valkey 链接系统 `libssl.so.1.1` 和 `libcrypto.so.1.1`。

当前未提供 openEuler aarch64 制品。

## 输入制品

| 文件 | 来源 | SHA-256 |
|---|---|---|
| `valkey-8.1.8.tar.gz` | `https://github.com/valkey-io/valkey/archive/refs/tags/8.1.8.tar.gz` | `0edc455ba7524f0cfa4f73fdc70b91dec6941e893a09bcbdd012470d08043cec` |
| `redis_exporter-v1.84.0.linux-amd64.tar.gz` | `https://github.com/oliver006/redis_exporter/releases/download/v1.84.0/redis_exporter-v1.84.0.linux-amd64.tar.gz` | `f13280147f1a0f6ed5f5d61ac80620c0b64049d76a99c7a5f043319efeb368fd` |

## 构建环境

- openEuler 22.03、x86_64；
- `gcc`、`make`、`openssl-devel`、`tar`、`gzip`、`strip`；
- 构建脚本：[../../build-valkey-openeuler.sh](../../build-valkey-openeuler.sh)。

## 构建

在仓库根目录执行：

```bash
bash package/build-valkey-openeuler.sh \
  /path/to/valkey-8.1.8.tar.gz \
  /path/to/redis_exporter-v1.84.0.linux-amd64.tar.gz \
  package/raw/packages/
```

脚本会校验输入 SHA-256，使用 `BUILD_TLS=yes` 构建 Valkey，打入静态链接的
redis_exporter 1.84.0，并在目标目录输出：

```text
package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
```

为支持 Nexus 上传的 MD5 幂等检查，再生成 sidecar：

```bash
VALKEY_ARTIFACT=package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
openssl md5 -r "$VALKEY_ARTIFACT" | awk '{print $1}' > "$VALKEY_ARTIFACT.md5"
```

## 当前受控制品

```text
文件：valkey-8.1.8-openeuler22.03-x86_64.tar.gz
SHA-256：031705868bba8060d8d6641bbaf2040e05272b752d7faefaa6ce63d1e3dc1200
MD5：4006b926af4a61399ddac50edae3e448
```

安装前至少确认：

```bash
shasum -a 256 package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
tar -tzf package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz | sed -n '1,30p'
```

该 tar 包与 `.md5` 文件由 `package/.gitignore` 默认忽略；需要将受控制品纳入 Git 时，
必须显式使用 `git add -f`。
