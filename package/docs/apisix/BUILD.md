# APISIX 3.17.0 openEuler Standalone 离线 RPM 包构建说明

本文说明如何在一台可使用 Docker 且可联网的构建机上，制作可部署到 `x86_64` openEuler 22.03 LTS-SP3 的 APISIX Standalone 离线 RPM 包。目标主机无需 Docker、无需访问互联网，使用系统自带 `dnf` 完成安装。

## 适用范围

| 项目 | 取值 |
| --- | --- |
| APISIX | `3.17.0` |
| 目标系统 | openEuler 22.03 LTS-SP3 |
| 目标架构 | `x86_64` |
| 部署模式 | `data_plane + yaml`（Standalone） |
| 输出包 | `apisix-3.17.0-openEuler-22.03-LTS-SP3-x86_64-standalone-rpm-r2.tar.gz` |

最终包 SHA-256：

```text
f97e3434e18626f455daf11af08c986a94db187690ea4b5d436a9deb2c65d0d8
```

## 包结构

```text
bundle/
├── conf/
│   ├── apisix.yaml
│   └── config.yaml
├── keys/
│   ├── APISIX-KEYS
│   └── RPM-GPG-KEY-openEuler
├── rpms/                         # 27 个 RPM
├── scripts/
│   ├── install.sh
│   └── verify.sh
├── BUILD-VALIDATION.txt
├── OFFLINE-SMOKE.txt
├── README.md
├── RPM-MANIFEST.txt
├── RPM-SIGNATURES.txt
└── SHA256SUMS
```

压缩包根目录必须直接包含上述内容，不能再额外包一层目录。DataSophon 元数据中的 `createDecompressDir: true` 会保持 `rpms/`、`conf/` 和 `scripts/` 目录。

## 为什么需要 `apisix-libcrypt-compat`

APISIX 3.17 官方 RPM 内置的 OpenResty 依赖 `libcrypt.so.2`；openEuler 22.03 LTS-SP3 默认只提供 `libcrypt.so.1`。不能用软链接伪造 ABI 兼容性。

应在 `openeuler/openeuler:22.03-lts-sp3` 的 `linux/amd64` 容器中从 libxcrypt `4.4.38` 构建私有兼容 RPM：

```text
apisix-libcrypt-compat-4.4.38-1.oe2203sp3.x86_64.rpm
```

该 RPM 将真实 `libcrypt.so.2` 安装到 `/opt/apisix-compat/lib64`，通过 `/etc/ld.so.conf.d/apisix-libcrypt-compat.conf` 和 `ldconfig` 生效，不覆盖系统的 `/lib64/libcrypt.so.1`。

构建 libxcrypt：

```bash
./configure \
  --prefix=/opt/apisix-compat \
  --libdir=/opt/apisix-compat/lib64 \
  --enable-hashes=strong,glibc \
  --enable-obsolete-api=no \
  --disable-static \
  --disable-failure-tokens
make -j"$(nproc)"
make check
make DESTDIR=/out/libcrypt-root install
```

RPM spec 的关键文件清单：

```spec
%files
%config(noreplace) /etc/ld.so.conf.d/apisix-libcrypt-compat.conf
/opt/apisix-compat/lib64/libcrypt.so.2
/opt/apisix-compat/lib64/libcrypt.so.2.0.0
```

## Standalone 配置

`config.yaml`：

```yaml
apisix:
  node_listen:
    - port: 9080

deployment:
  role: data_plane
  role_data_plane:
    config_provider: yaml
```

`apisix.yaml`：

```yaml
routes: []
#END
```

`#END` 必须单独占一行。此模式不依赖 etcd，且不启用 9180 Admin API。

## Docker 构建流程

Dockerfile 固定使用 `--platform=linux/amd64`，并按以下四个阶段构建：

1. `downloader`：基于 `openeuler/openeuler:22.03-lts-sp3`，下载 APISIX、openEuler RPM 依赖和 RPM GPG 公钥。
2. `compat-builder`：构建 libxcrypt `4.4.38` 与 `apisix-libcrypt-compat` RPM。
3. `runtime`：用 `dnf install --disablerepo='*' /out/rpms/*.rpm` 验证纯本地 RPM 事务。
4. `validator`：写入 Standalone 配置，执行 APISIX 初始化、配置校验和动态库检查。

构建命令：

```bash
docker build --platform linux/amd64 \
  -t codex/apisix-rpm-builder:3.17.0-openeuler22.03-sp3-x86_64 \
  context/
```

验证 OpenResty 动态库：

```bash
ldd /usr/local/openresty/nginx/sbin/nginx | grep 'not found'
```

命令必须无输出；`libcrypt.so.2` 应解析到 `/opt/apisix-compat/lib64/libcrypt.so.2`。

## 下载 RPM 与依赖闭包

下列命令不足以覆盖完整系统上可能发生的旧包升级：

```bash
dnf install --downloadonly --destdir=/out/rpms apisix-3.17.0
```

除 APISIX 自动解析的依赖外，必须显式下载这些配套运行库：

```text
cyrus-sasl-lib
libyaml
openldap
systemd-libs
systemd-udev
libblkid
libmount
libsmartcols
libuuid
```

下载示例：

```bash
dnf install -y 'dnf-command(download)'

dnf install -y --downloadonly --destdir=/out/rpms apisix-3.17.0
dnf download --destdir=/out/rpms \
  cyrus-sasl-lib libyaml openldap systemd-libs systemd-udev \
  libblkid libmount libsmartcols libuuid
```

最终应有 27 个 RPM：26 个发行方签名 RPM，加 1 个自建兼容 RPM。补充库用于确保目标主机已有旧版 `systemd`、`util-linux`、`openldap` 等包时，离线 DNF 仍可完成版本一致的升级事务。

## 严格完整性校验

在 `bundle/` 下生成文件清单和哈希：

```bash
find . -type f ! -name SHA256SUMS | LC_ALL=C sort | \
  while IFS= read -r file; do shasum -a 256 "${file}"; done > SHA256SUMS
```

安装脚本必须先比较 `SHA256SUMS` 中的文件列表与实际文件列表，再执行：

```bash
sha256sum -c SHA256SUMS
```

这样额外插入到 `rpms/` 的文件也会被拒绝。导入 `APISIX-KEYS` 与 `RPM-GPG-KEY-openEuler` 后，官方 RPM 的 `rpm -K` 结果必须为 `digests signatures OK`；自建兼容 RPM 至少为 `digests OK`，并受整包 SHA-256 保护。

## 安装脚本要求

`scripts/install.sh` 应依次完成：

1. 检查 root、`x86_64`、`/etc/openEuler-release` 是 `22.03 LTS-SP3`。
2. 校验所有文件 SHA-256 和 RPM 签名/摘要。
3. 执行纯离线事务：

   ```bash
   dnf install -y --disablerepo='*' --setopt=localpkg_gpgcheck=0 rpms/*.rpm
   ```

4. 备份 RPM 默认的 `config.yaml`，写入 Standalone 的 `config.yaml` 与 `apisix.yaml`。
5. 为登录 Shell 与 systemd service 写入 OpenResty PATH：

   ```text
   /usr/local/openresty/luajit/bin
   /usr/local/openresty/nginx/sbin
   /usr/local/openresty/bin
   ```

6. 执行 `apisix init`、`apisix test`、`systemctl daemon-reload` 和 `systemctl enable --now apisix`。

## 归档和目标机安装

在 macOS 上打包时禁用扩展属性：

```bash
archive=apisix-3.17.0-openEuler-22.03-LTS-SP3-x86_64-standalone-rpm-r2.tar.gz
COPYFILE_DISABLE=1 tar --no-xattrs -czf "${archive}" -C bundle .
shasum -a 256 "${archive}" > "${archive}.sha256"
```

目标机安装：

```bash
sha256sum -c "${archive}.sha256"
mkdir apisix-offline && tar -xzf "${archive}" -C apisix-offline
cd apisix-offline
sudo ./scripts/install.sh
sudo ./scripts/verify.sh
```

`systemctl is-active apisix` 应为 `active`。`curl -I http://127.0.0.1:9080/` 在初始空路由下返回 `404` 且含 `Server: APISIX/3.17.0` 是正常结果。

## 已验证结果

2026-07-17 已在 `192.168.10.132` 验证：openEuler 22.03 LTS-SP3、`x86_64`、全包 SHA-256、RPM 签名/摘要、DNF transaction test、实际安装、`apisix test`、systemd 启动和 9080 跨主机访问均通过；9180 未监听。

安装事务会按目标系统现状升级必要的配套系统包。本次验证中安装 6 个包、升级 13 个包、无删除项；升级 `systemd` 时生成了 `/etc/systemd/system.conf.rpmnew`，原有 `system.conf` 保留。
