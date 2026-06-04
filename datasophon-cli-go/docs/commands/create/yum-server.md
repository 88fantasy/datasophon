# datasophon-cli create yum-server

## 用途

在指定节点配置 httpd / apache2 离线包源服务，把本地 `packages/os/<arch>/<os>/` 目录暴露为 HTTP 静态文件服务，供其他节点通过 `init offlineSlave` 配为本地 yum/apt 源。命令底层复用 `init.InitOfflineServer` Task 实现。

支持两种模式：

- **配置文件模式**（`-c` 指定配置文件）：从 `cluster-sample.yml` 的 `global.yumServer` 读取节点与端口，SSH 到 `yumServer.node` 远程执行；安装成功后将 `global.yumServer.enable` 回写为 `true`。
- **手动模式**（不指定 `-c`）：所有参数通过命令行传入，在**本地节点**执行。

> 此命令始终以 `EnableRegistry=false` 调用底层 Task，即**不**从 Nexus 拉取安装包（只使用本地 packages/）。

## 用法 (Synopsis)

```bash
# 配置文件模式
datasophon-cli [--dry-run] create yum-server \
  -c <config.yml> --datasophonPath <path>

# 手动模式
datasophon-cli [--dry-run] create yum-server \
  -p <packagePath> --serverIp <ip> --serverPort <port>
```

## 参数 / Flags

|        flag        |  简写  |   类型   |  默认  |    必填    |                               说明                               |
|--------------------|------|--------|------|----------|----------------------------------------------------------------|
| `--config`         | `-c` | string | `""` | 否        | 配置文件路径；指定后进入配置文件模式                                             |
| `--datasophonPath` | 无    | string | `""` | 配置文件模式必填 | datasophon 根目录（推导 `<datasophonPath>/datasophon-init/packages`） |
| `--packagePath`    | `-p` | string | `""` | 手动模式必填   | 安装包根目录（须包含 `os/<arch>/<os>/` 结构）                               |
| `--serverIp`       | 无    | string | `""` | 手动模式必填   | httpd 服务绑定 IP                                                  |
| `--serverPort`     | 无    | string | `""` | 手动模式必填   | httpd 服务端口                                                     |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 配置文件依赖（配置文件模式）

|              字段               |                             说明                             |
|-------------------------------|------------------------------------------------------------|
| `global.yumServer.node`       | yumServer 节点 hostname（须在 `nodes` 列表中；其 `ip` 用作 `serverIp`） |
| `global.yumServer.listenPort` | httpd 监听端口（用作 `serverPort`）                                |
| `global.sshAuthType`          | SSH 鉴权方式                                                   |

## 包目录结构约定

`packagePath` 下必须包含 `os/<arch>/<os>/` 形式的目录结构：

```
<packagePath>/
└── os/
    ├── x86_64/
    │   ├── centos7/
    │   ├── openEuler-22.03-LTS-SP3/
    │   └── ubuntu-22.04/
    └── aarch64/
        └── openEuler-22.03-LTS-SP3/
```

httpd / apache2 服务会将这个目录暴露为 `http://<serverIp>:<serverPort>/`。其他节点通过 [`init offlineSlave`](../init/repo/offlineslave.md) 配置 baseURL 即可消费。

## 示例

### 配置文件模式

```bash
datasophon-cli create yum-server \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --datasophonPath /data/datasophon
# 成功后：global.yumServer.enable: true 回写
```

### 手动模式

```bash
datasophon-cli create yum-server \
  -p /data/datasophon/datasophon-init/packages \
  --serverIp 192.168.1.10 \
  --serverPort 4080
```

## 退出码 / 常见错误

|                错误信息                |                   根因                   |                         处置                         |
|------------------------------------|----------------------------------------|----------------------------------------------------|
| `配置文件模式下 --datasophonPath 为必填项`    | 指定了 `-c` 但未给 `--datasophonPath`        | 补全 `--datasophonPath`                              |
| `--datasophonPath 必须是绝对路径（以 / 开头）` | 传入了相对路径                                | 改用 `/` 开头的绝对路径                                     |
| `配置中未找到 yumServer 节点: <hostname>`  | `global.yumServer.node` 不在 `nodes` 列表中 | 检查 hostname 拼写                                     |
| `手动模式下以下参数为必填项: <list>`            | 手动模式缺参数                                | 补全 `--packagePath` / `--serverIp` / `--serverPort` |
| `安装成功但写回配置文件失败`                    | 配置文件写权限不足                              | 检查写权限                                              |

## 相关命令

- [`init offlineSlave`](../init/repo/offlineslave.md) — 其他节点配置使用本命令安装的离线源
- [`create cluster`](./cluster.md) — 集群初始化（DAG 步骤 14 `init-offline-server` 复用同一 `InitOfflineServer` Task）

