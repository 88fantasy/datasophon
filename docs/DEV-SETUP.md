# 开发环境搭建指南

> 本文档指导开发者在 Windows、Ubuntu、macOS 上搭建 DataSophon 开发环境。
> 适用版本：3.0-SNAPSHOT

---

## 目录

- [1. 环境要求总览](#1-环境要求总览)
- [2. Java 17 安装](#2-java-17-安装)
- [3. Maven 配置](#3-maven-配置)
- [4. Go 1.21+ 安装](#4-go-121-安装)
- [5. Node.js / pnpm 安装](#5-nodejs--pnpm-安装)
- [6. 网络代理配置](#6-网络代理配置)
- [7. 项目构建](#7-项目构建)
- [8. 运行测试](#8-运行测试)
- [9. 单模块开发](#9-单模块开发)
- [10. 常见问题](#10-常见问题)

---

## 1. 环境要求总览

| 依赖 | 最低版本 | 用途 |
|---|---|---|
| Java (JDK) | 17 | 后端编译运行 |
| Maven | 3.8.4（wrapper 自带） | 构建管理 |
| Go | 1.21 | CLI 工具编译 |
| Node.js | 20.x | 前端构建（Maven 自动下载，本地开发可选） |
| pnpm | 8.x | 前端包管理（Maven 构建时自动安装） |
| Git | 2.x | 版本控制 |

> **Maven Wrapper**：项目自带 `mvnw`（Unix）/ `mvnw.cmd`（Windows），会自动下载 Maven 3.8.4，无需手动安装 Maven。

---

## 2. Java 17 安装

### Windows

**方式 A — Adoptium (推荐)**

```powershell
# 使用 winget
winget install EclipseAdoptium.Temurin.17.JDK

# 或手动下载：https://adoptium.net/temurin/releases/?version=17
```

**方式 B — Oracle JDK**

下载地址：https://www.oracle.com/java/technologies/downloads/#java17

安装后配置环境变量：

```powershell
# 系统环境变量
JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot
PATH 追加  = %JAVA_HOME%\bin
```

### Ubuntu / Debian

```bash
# 方式 A — Adoptium (推荐)
sudo apt install -y wget apt-transport-https gpg
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-17-jdk

# 方式 B — OpenJDK
sudo apt install -y openjdk-17-jdk
```

配置环境变量：

```bash
echo 'export JAVA_HOME=/usr/lib/jvm/temurin-17-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### macOS

```bash
# 使用 Homebrew (推荐)
brew install --cask temurin@17

# 或 OpenJDK
brew install openjdk@17
```

配置环境变量（zsh）：

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
source ~/.zshrc
```

### 验证

```bash
java -version
# openjdk version "17.0.x" ...
```

---

## 3. Maven 配置

项目自带 Maven Wrapper（`./mvnw` / `mvnw.cmd`），**无需手动安装 Maven**。

### Windows 使用方式

```powershell
# 使用 mvnw.cmd（不是 ./mvnw）
mvnw.cmd clean package -DskipTests

# 或在 Git Bash 中
./mvnw clean package -DskipTests
```

### Linux / macOS 使用方式

```bash
./mvnw clean package -DskipTests
```

### 配置国内镜像（可选但推荐）

编辑 `~/.m2/settings.xml`（没有则创建）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

---

## 4. Go 1.21+ 安装

仅 `datasophon-cli-go` 模块需要。如果只做后端/前端开发，可跳过。

### Windows

```powershell
# 方式 A — winget
winget install GoLang.Go.1.21

# 方式 B — 手动下载
# https://go.dev/dl/
```

安装后默认路径：`C:\Program Files\Go`

### Ubuntu / Debian

```bash
# 方式 A — 官方 tarball
wget https://go.dev/dl/go1.21.13.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go1.21.13.linux-amd64.tar.gz
echo 'export PATH=/usr/local/go/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 方式 B — apt (版本可能较旧)
sudo apt install -y golang-go
```

### macOS

```bash
brew install go@1.21
```

### 配置 Go 代理（国内必须）

```bash
go env -w GOPROXY=https://goproxy.cn,direct
```

> **不配置此步，`datasophon-cli-go` 模块编译会因网络超时失败。**

### 验证

```bash
go version
# go version go1.21.x ...
```

---

## 5. Node.js / pnpm 安装

前端开发需要。Maven 构建时 `frontend-maven-plugin` 会**自动下载 Node v20.19.2**到项目内，无需手动安装。但本地调试前端需要手动安装。

### 方式 A — nvm (推荐，多版本管理)

**Windows (nvm-windows)**：

```powershell
# 下载安装 nvm-windows：https://github.com/coreybutler/nvm-windows/releases
nvm install 20.19.2
nvm use 20.19.2
npm install -g pnpm
```

**Linux / macOS (nvm)**：

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc  # 或 ~/.zshrc
nvm install 20
nvm use 20
npm install -g pnpm
```

### 方式 B — 直接安装

```bash
# Ubuntu
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
sudo npm install -g pnpm

# macOS
brew install node@20
npm install -g pnpm

# Windows
winget install OpenJS.NodeJS.LTS
npm install -g pnpm
```

### 验证

```bash
node -v    # v20.x.x
pnpm -v    # 8.x.x 或 9.x.x
```

---

## 6. 网络代理配置

国内开发环境通常需要配置代理。以下根据实际代理地址调整。

### Maven 代理

编辑 `~/.m2/settings.xml`：

```xml
<settings>
  <proxies>
    <proxy>
      <id>http-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>127.0.0.1</host>
      <port>7890</port>
    </proxy>
    <proxy>
      <id>https-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>127.0.0.1</host>
      <port>7890</port>
    </proxy>
  </proxies>
</settings>
```

### Go 代理

```bash
go env -w GOPROXY=https://goproxy.cn,direct
```

### npm / pnpm 代理

```bash
npm config set registry https://registry.npmmirror.com
pnpm config set registry https://registry.npmmirror.com
```

### Git 代理

```bash
git config --global http.proxy http://127.0.0.1:7890
git config --global https.proxy http://127.0.0.1:7890
```

---

## 7. 项目构建

### 首次构建（全量）

```bash
# Windows (Git Bash 或 WSL)
./mvnw clean package -DskipTests

# Windows (PowerShell / CMD)
mvnw.cmd clean package -DskipTests

# Linux / macOS
./mvnw clean package -DskipTests
```

构建产物：

| 模块 | 产物路径 |
|---|---|
| datasophon-api | `datasophon-api/target/datasophon-api-3.0-SNAPSHOT.jar` |
| datasophon-worker | `datasophon-worker/target/datasophon-worker-3.0-SNAPSHOT/` |
| datasophon-cli-go | `datasophon-cli-go/target/dist/datasophon-cli-linux-amd64` |
| datasophon-k8s-agent | `datasophon-k8s-agent/target/datasophon-k8s-agent-3.0-SNAPSHOT.jar` |
| datasophon-ui | `datasophon-ui/dist/` |
| datasophon-assembly | `datasophon-assembly/target/datasophon-3.0-SNAPSHOT/` |

### 加速构建

```bash
# 跳过测试 + 跳过前端（如果不需要重新构建 UI）
./mvnw clean package -DskipTests -pl '!datasophon-ui'

# 跳过测试 + 跳过 CLI-Go（如果不需要 Go 二进制）
./mvnw clean package -DskipTests -pl '!datasophon-cli-go'

# 并行构建（2倍 CPU 核心数）
./mvnw clean package -DskipTests -T 2C
```

### 构建单个模块

```bash
# 只编译 worker 模块（自动编译依赖的 common、grpc-api）
./mvnw compile -pl datasophon-worker -am

# 只编译 api 模块
./mvnw compile -pl datasophon-api -am
```

---

## 8. 运行测试

```bash
# 全量测试
./mvnw test

# 单模块测试
./mvnw test -pl datasophon-worker

# 单个测试类
./mvnw test -pl datasophon-worker -Dtest=WorkerCommandGrpcServiceTest

# 单个测试方法
./mvnw test -pl datasophon-worker -Dtest=WorkerCommandGrpcServiceTest#ping_returnsPong
```

> **注意**：`datasophon-worker` 模块只运行 `**/grpc/**Test.java` 匹配的测试。
> `datasophon-api` 模块排除了 `MetaUtilsTaskTest` 和 `NexusUtilsTaskTest`。

---

## 9. 单模块开发

### 后端开发（datasophon-api / datasophon-worker）

```bash
# 编译
./mvnw compile -pl datasophon-api -am

# 运行测试
./mvnw test -pl datasophon-api -Dtest=YourTestClass

# 打包（跳过其他模块）
./mvnw package -DskipTests -pl datasophon-api -am
```

IDE 导入：直接导入根目录的 `pom.xml` 作为 Maven 项目。IDEA 会自动识别多模块结构。

### 前端开发（datasophon-ui）

```bash
cd datasophon-ui
pnpm install
pnpm dev          # 启动开发服务器
pnpm test         # 运行测试
pnpm build        # 生产构建
```

### Go CLI 开发（datasophon-cli-go）

```bash
cd datasophon-cli-go
go build -o dist/datasophon-cli ./cmd/datasophon-cli   # 编译
go test ./...                                           # 运行测试
go vet ./...                                            # 静态检查
```

---

## 10. 常见问题

### Q1：`mvnw.cmd` 报错 "此时不应有 xxx"

Windows CMD 不支持 `./mvnw`，必须用 `mvnw.cmd`。Git Bash 中可用 `./mvnw`。

### Q2：Go 模块下载超时

```
dial tcp xxx:443: connectex: A connection attempt failed...
```

解决：

```bash
go env -w GOPROXY=https://goproxy.cn,direct
```

### Q3：前端构建报错 "pnpm: command not found"

Maven 的 `frontend-maven-plugin` 使用自己安装的 npm 环境，不会自动找到全局 pnpm。已在 `datasophon-ui/pom.xml` 中添加了 `npm install -g pnpm` 步骤。如果仍然失败，手动执行：

```bash
cd datasophon-ui
npx pnpm install
npx pnpm build
```

### Q4：`datasophon-k8s-agent` 打包报错 tar exit 2

Windows 上 `tar` 会把 `E:` 盘符误解为磁带设备。已修复为相对路径。如果仍遇到，确保在 Git Bash 或 WSL 中执行构建。

### Q5：Maven 报 `${revision}` 解析失败

```
Could not find artifact com.datasophon:datasophon:pom:${revision}
```

这是 Maven 缓存问题，加 `-U` 强制更新：

```bash
./mvnw clean package -DskipTests -U
```

### Q6：`JAVA_HOME` 未设置或版本不对

```bash
# 检查当前 Java 版本
java -version

# 检查 JAVA_HOME
echo $JAVA_HOME        # Linux/macOS
echo %JAVA_HOME%       # Windows CMD
$env:JAVA_HOME         # Windows PowerShell
```

确保指向 JDK 17 路径，且 `java -version` 输出 17.x。

### Q7：Windows 上 Shell 脚本执行失败

项目中的 `.sh` 脚本需要在 Git Bash 或 WSL 中执行，不支持 Windows CMD/PowerShell。

推荐方案：
- 安装 Git for Windows（自带 Git Bash）
- 或使用 WSL2 + Ubuntu

### Q8：Maven 构建时 Node 下载慢

`frontend-maven-plugin` 配置了国内镜像（`npmmirror.com`），正常情况下下载很快。如果仍然慢，可手动下载 Node 并指定路径：

```xml
<!-- datasophon-ui/pom.xml -->
<configuration>
    <nodeDownloadRoot>https://npmmirror.com/mirrors/node/</nodeDownloadRoot>
</configuration>
```

---

## 附录：快速验证脚本

在项目根目录执行以下命令，验证所有工具是否就绪：

```bash
echo "=== Java ==="
java -version 2>&1 | head -1

echo "=== Maven ==="
./mvnw --version 2>&1 | head -1

echo "=== Go ==="
go version 2>&1

echo "=== Go Proxy ==="
go env GOPROXY

echo "=== Node ==="
node -v 2>&1

echo "=== pnpm ==="
pnpm -v 2>&1

echo "=== Git ==="
git --version
```

预期输出示例：

```
=== Java ===
openjdk version "17.0.x" ...
=== Maven ===
Apache Maven 3.8.4 ...
=== Go ===
go version go1.21.x ...
=== Go Proxy ===
https://goproxy.cn,direct
=== Node ===
v20.x.x
=== pnpm ===
8.x.x
=== Git ===
git version 2.x.x
```
