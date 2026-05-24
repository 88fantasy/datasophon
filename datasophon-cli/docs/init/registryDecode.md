# init registryDecode — 解压/解密制品包

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitRegistryDecode.java`

## 用途

在全量制品包交付场景下，将加密/编码后的交付物解密，并将配置文件和安装包分发到 Datasophon 工作目录中。适用于离线交付时将整体加密的制品包初始化为可用状态。

---

## 参数

独有参数 5 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-e` | `--enable` | 否 | `false` | `false` 时直接跳过整个命令。**必须设为 `true` 才会执行。** |
| `-d` | `--datasophonHomePath` | **是** | — | Datasophon 主目录（如 `/opt/datasophon`） |
| `-cn` | `--productConfigPath` | **是** | — | 制品配置包路径（含 `common.properties` 和 `cluster-sample.yml`） |
| `-pn` | `--productPackagesPath` | **是** | — | 制品安装包路径（含 `raw/packages/` 子目录） |
| `-de` | `--decode` | 否 | `false` | 是否同时解密 `cluster-sample.yml`（`true`=解密，`false`=不解密） |

> 解密密钥通过继承的 `-cpwd` 参数传入。

---

## 示例

```bash
java -jar datasophon-cli.jar init registryDecode \
  -e true \
  -d /opt/datasophon \
  -cn /mnt/delivery/config \
  -pn /mnt/delivery/packages \
  -de true \
  -cpwd <your-decrypt-key>
```

---

## 行为说明

### 执行步骤

1. 检查以下路径是否存在（任一不存在则抛出 `ExecutionException`）：
   - `datasophonHomePath`
   - `productConfigPath`
   - `productPackagesPath`
   - `productPackagesPath/raw/packages/`
   - `productConfigPath/common.properties`
   - `productConfigPath/datasophon-init/cluster-sample.yml`（注意：路径中包含 `datasophon-init/`）
   - `datasophonHomePath/datasophon-init/`
2. 若 `common.properties` 内容是 Base64 编码（已加密），用 `-cpwd` 密钥解密后原地覆盖。
3. 若 `-de=true` 且 `cluster-sample.yml` 是 Base64 编码，同样解密覆盖。
4. 复制配置文件：
   - `common.properties` → `<datasophonHomePath>/conf/`
   - `cluster-sample.yml` → `<datasophonHomePath>/datasophon-init/config/`
5. 若 `datasophon-init/packages/` 不存在，将 `productPackagesPath/raw/packages/` 复制进去。

---

## 注意事项

- `-e false`（默认）时命令完全跳过，**不是报错**——这是设计行为，用于在非制品交付场景中安全传参。
- 解密使用 Jasypt + Base64 检测（`Base64.isBase64()`），未加密的明文配置文件不会被误操作。
- `common.properties` 中通常含数据库连接信息、加密盐等敏感配置，解密后立即保护文件权限。
- 此命令通常在 `create cluster` 之前单独执行，作为"制品包解封"步骤。
