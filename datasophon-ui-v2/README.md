# Datasophon UI v2

Datasophon 当前默认前端。项目基于 React 19、Umi Max 4、Ant Design 6 和 ProComponents 3，生产构建产物由 Maven 复制到 `datasophon-api` 的静态资源目录，并最终进入 Manager 发布包。

旧 `datasophon-ui/` 已退出根 Maven reactor，仅保留历史参考；新增功能和修复统一在本模块完成。

## 当前能力

- 登录、用户管理、集群创建和授权。
- 物理集群主机接入、服务安装、角色分配、配置、日志和 DAG 命令查看。
- Kubernetes 集群配置、资源展示、服务部署，以及 `IMPORTED` 集群只读接管向导。
- 集群概览、告警、OpenTelemetry Collector 管理、服务监控看板和 Trace 拓扑。
- Gravitino 实时/历史血缘、数据集变更和 Flink 作业流速展示。
- 基于 Ant Design X 的 AI 运维助手页面。

路由事实源是 `config/routes.ts`，页面代码位于 `src/pages/`。

## 技术栈

| 项目 | 当前版本或实现 |
|---|---|
| Runtime | React `19.2.x`、React DOM `19.2.x` |
| Framework | Umi Max `4.6.x` |
| UI | Ant Design `6.4.x`、ProComponents `3.1.x`、Ant Design X `2.7.x` |
| 图形与编辑器 | AntV G6 `5.1.x`、X6 `3.1.x`、Monaco Editor `0.55.x` |
| 样式 | Tailwind CSS 4、antd-style、CSS Modules / Less |
| 测试 | Vitest |
| 质量检查 | Biome + TypeScript `tsc --noEmit` |
| 包管理 | npm，锁文件为 `package-lock.json` |

## 环境要求

- Node.js `>=22.0.0`。
- 使用 npm；不要使用 pnpm 或 yarn 更新依赖和锁文件。
- Maven reactor 使用 `frontend-maven-plugin` 下载 Node `22.14.0` 和 npm `10.9.2`。

## 安装与开发

```bash
cd datasophon-ui-v2
npm install

# 开发服务器，使用本地 mock
npm start

# 开发服务器，不加载 mock，通过 config/proxy.ts 连接后端
npm run dev
```

开发服务器默认监听 `8000`。`config/proxy.ts` 的 `dev` 配置把 `/ddh` API 请求转发到 `datasophon-api`；生产构建不使用此代理。

常用地址：

| 场景 | 地址 |
|---|---|
| 前端开发 | `http://127.0.0.1:8000` |
| 默认后端 | `http://127.0.0.1:8080/ddh` |
| REST API v2 | `/ddh/api/v2/**` |

真实后端初始账号为 `admin` / `DJEutbydS@U%f7Jb`。mock 数据与真实后端账号相互独立。

## 构建与验证

```bash
# 生产构建
npm run build

# Biome lint + TypeScript 类型检查
npm run lint

# 分项检查
npm run biome:lint
npm run tsc

# 单元测试
npm run test
npm run test:coverage

# 监听模式
npm run test:watch
```

也可从仓库根目录随 Maven 构建：

```bash
./mvnw -pl datasophon-ui-v2 -am package -DskipTests
```

Maven 在 `generate-resources` 阶段执行 `npm install` 和 `npm run build`，再把 `dist/index.html` 与其余静态资源复制到根目录 `static/`，供 `datasophon-api` 打包。

## 目录结构

```text
datasophon-ui-v2/
├── config/
│   ├── config.ts            # Umi、插件、主题和构建配置
│   ├── routes.ts            # 路由事实源
│   ├── proxy.ts             # 仅开发期代理
│   └── publicPath.ts        # 开发/生产静态资源前缀
├── mock/                    # 全局 mock
├── scripts/                 # Monaco 等构建辅助脚本
├── src/
│   ├── pages/               # 业务页面
│   ├── components/          # 共享组件
│   ├── services/            # API 请求和对应类型
│   ├── locales/             # 国际化资源
│   ├── app.tsx              # Umi 运行时入口
│   └── requestErrorConfig.ts
├── biome.json
├── vitest.config.ts
├── package.json
└── package-lock.json
```

## 开发约定

- API 调用集中在 `src/services/` 或页面同目录的 `service.ts`；服务签名变化时同步更新 `src/services/types/`。
- 页面路由只在 `config/routes.ts` 注册，路由 `name` 需要对应 `src/locales/` 菜单文案。
- 复杂服务端状态可使用 React Query；表格列表优先沿用 ProTable 的 `request` 模式。
- 布局样式优先使用 Tailwind CSS，再使用 antd-style/CSS Modules；不要引入 ESLint、Prettier 或另一套格式化器。
- `src/.umi/`、`dist/` 和根目录 `static/` 都是生成产物，不应手工维护。
- `npm run biome` 会自动改写源码；仅在明确需要格式化时运行，并检查 diff。
- 修改 Ant Design 组件前先执行 `npx antd info <Component>` 核对当前版本 API；提交前可执行 `npx antd lint ./src` 做专项检查。

## 离线资源

Monaco、字体、登录背景和运行期静态资源均由仓库本地提供。`prepare`、`prestart`、`predev` 和 `prebuild` 会运行 `scripts/copy-monaco.mjs`，不要重新引入依赖公网 CDN 的资源。

## 与后端的集成

- Umi `base` 为 `/ddh`，生产静态资源由 Manager 在同一上下文下提供。
- REST 请求统一使用 `/ddh/api/**` 或 `/ddh/api/v2/**`。
- 401 会回到登录页；会话与 CSRF 处理集中在请求配置和认证服务中。
- AI 对话由 `/ddh/api/v2/chat/completions` 反向代理到可选 `datasophon-ai-agent` sidecar。

根项目构建、端口和模块关系见 [仓库 README](../README.md)。
