# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-assembly

本模块是 Datasophon 的 Maven 顶层 assembly 打包模块，负责把多个子模块（`datasophon-api`、`datasophon-worker`、`datasophon-cli`）的构建产物组装成最终交付介质 `datasophon-${project.version}-package.tar.gz`。模块本身 **没有 Java 源码**，`packaging` 为 `pom`，构建后不会产出可独立运行的 Java artifact；它的全部价值就是"打包"。

### 职责

- 在 `prepare-package` 阶段，通过 `maven-dependency-plugin` 的 `unpack-dependencies` 目标，把 `datasophon-api` 和 `datasophon-worker` 的 `tar.gz` 依赖解包到 `target/unpack/`。
- 在 `package` 阶段，通过 `maven-assembly-plugin`（version 3.3.0）读取 `assembly/assembly.xml` 描述符，组装出最终目录结构：
  - `datasophon-manager-${version}/`：API 模块解包后的整棵目录树（含 `bin/`、`conf/`、`logs/`、`lib/`、`release.txt` 等）。
  - `datasophon-worker/`：Worker 模块解包后的整棵目录树。
  - `datasophon-cli/bin/`：从 `datasophon-cli-go/target/dist` 取 `datasophon-cli-linux-amd64` 与 `datasophon-cli-linux-arm64` 两个二进制，权限 `0755`。
  - `datasophon-cli/config/cluster-sample.yml`：CLI 集群配置样例。
  - `datasophon-cli/README.md`、`datasophon-cli/release.txt`：CLI 文档与 release 元信息。
- 输出文件位于 `target/datasophon-${project.version}-package.tar.gz`（`finalName=datasophon-${project.version}`，`appendAssemblyId=false`）。

### 关键文件

- `assembly/assembly.xml`：assembly 描述符，定义 tar.gz 内每个目录/文件的来源、输出路径、文件权限（`0755`/`0644`）。**任何对最终介质目录结构的调整（增删子模块、改权限、改路径）都改这里。**
- `assembly/release.txt`：release info 模板，文件内含 `${project.version}`、`${git.branch}`、`${git.commit.id}` 等属性占位符；它由 `datasophon-cli-go` 在其 `target/release.txt` 渲染具体版本信息，再被 `assembly.xml` 拷进 tar.gz 的 `datasophon-cli/` 目录。
- `pom.xml`：声明对 `datasophon-api`、`datasophon-worker`（`type=tar.gz`，`scope=provided`）的依赖，配置 `maven-dependency-plugin`（解包）与 `maven-assembly-plugin`（组装）。

### 修改介质内容时的原则

- 想调整最终 tar.gz 内的目录布局、文件权限、新增/删除某个子目录、引入新子模块的产物 → **优先改本模块**（`pom.xml` + `assembly/assembly.xml`），而不是去改 `datasophon-api` 等子模块的源文件。
- 若要替换某个子模块的产物（例如改了 API 的启动脚本或 CLI 二进制），需先在对应子模块执行 `mvn install`，让新的 `tar.gz` 进入本地仓库，再回到本模块执行 `mvn package` 重新组装。

### 构建特性

- 没有 Java 源码，没有 `src/main/java` 等源码目录。
- 不参与服务运行时，只参与交付物打包。
- 父 POM 的 `${revision}` 属性会透传到这里，最终体现在 tar.gz 文件名 `datasophon-${project.version}-package.tar.gz` 中。
