# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-ui

Datasophon 的前端模块。React 19 + Ant Design 6 + Ant Design Pro 2.8 + TypeScript + Vite 6 + pnpm。
最终构建产物 `dist/` 由 Maven 的 `frontend-maven-plugin` 在父项目构建链中自动产出，再被 `datasophon-api` 通过 assembly 内嵌到 tar 包并对外提供静态资源。

架构上下文见 `/Users/huzekang/opensource/datasophon/docs/ARCHITECTURE.md` 第 6 章「前端」。

---

## 1. 常用命令

包管理器固定为 `pnpm@10.11.0`，请勿用 npm / yarn / bun。

```bash
pnpm install            # 安装依赖（Node 推荐 20.19.2，与 pom.xml 中 frontend-maven-plugin 一致）
pnpm dev                # 启动 Vite 开发服务器（端口 5180，base = /ddh）
pnpm build              # 生产构建，产物输出到 dist/，assetsDir=static
pnpm preview            # 本地预览生产构建
pnpm lint               # 运行 ESLint
pnpm test               # 运行 Vitest（watch 模式）
pnpm test run           # 单次跑全部测试
pnpm test:coverage      # v8 覆盖率，HTML/JSON/text 输出到 coverage/
pnpm deploy             # node deploy.sh.js，将 dist 复制到 ../datasophon-api/src/main/resources/front/
```

### 运行单个测试

```bash
pnpm test -- src/components/Common/CommonTable/index.test.tsx
pnpm test -- -t "should render ProTable"
pnpm test -- src/components/ComponentName
```

### Maven 集成构建

在仓库根目录跑 `./mvnw -pl datasophon-ui -am package` 时：

1. `frontend-maven-plugin` 自动下载 Node `v20.19.2` + npm `10.8.2`（从 npmmirror 镜像）
2. 执行 `npm run build`（等价于 `pnpm install && vite build`）
3. `maven-resources-plugin` 将 `dist/` 同步到 `${project.parent.basedir}/static`
4. `datasophon-api` 的 assembly 将整个 `dist/` 打入最终 tar 包

注意：本模块 Maven `artifactId = datasophon-ui`，被 `datasophon-api` 作为构建依赖引入，从而触发前端构建。npm 包名（`package.json` 中 `name`）为 `ddh`，会被 `vite.config.ts` 取作 `base = /ddh`，这是上下文路径，必须与后端的 `server.servlet.context-path=/ddh` 一致。

---

## 2. 技术栈

| 关注点 | 选型 |
|---|---|
| 框架 | React 19、Ant Design 6、Ant Design Pro Components 2.8（`@ant-design/pro-components`） |
| 语言 | TypeScript 5.8，`tsconfig.app.json` 中 `strict: false`、`noUnusedLocals/Parameters: true`、`verbatimModuleSyntax: true`、`jsx: react-jsx` |
| 构建 | Vite 6（`@vitejs/plugin-react-swc`），SWC 加速 Fast Refresh |
| 样式 | Tailwind CSS 4（`@tailwindcss/vite`），`preflight: false` 避免覆盖 Antd 默认样式；less 用于个别旧样式 |
| 路由 | `react-router-dom` 7，`createBrowserRouter` + 懒加载，入口 `src/routes/index.tsx` |
| HTTP | Axios 0.22，封装在 `src/api/request.ts`（`axiosGet/axiosPost/axiosJsonPost/axiosPostUpload`），拦截器在 `src/api/interceptors.ts`（401 自动重登录） |
| 代码编辑器 | `@monaco-editor/react` + Monaco 0.52 + `@shikijs/monaco` + `sql-formatter` |
| 拓扑可视化 | `@antv/x6` + `@antv/x6-react-shape` + `@antv/x6-plugin-minimap` + `@antv/g6` + `@dagrejs/dagre` + `@antv/layout` + `elkjs` |
| 工具库 | `lodash-es`、`radash`、`dayjs`、`qs`、`js-cookie`、`js-yaml`、`spark-md5`、`hash-wasm`、`sm-crypto`、`eventemitter3` |
| 测试 | Vitest 4.1.2 + jsdom + `@testing-library/react` 16，覆盖率 `@vitest/coverage-v8` |
| 包管理器 | pnpm 10.11.0（`packageManager` 字段锁定） |

Antd 6 + React 19 兼容补丁 `@ant-design/v5-patch-for-react-19` 已安装但默认未启用（参考 `src/main.tsx` 中的注释 import）。

---

## 3. 状态管理与数据流

- **不引入 Redux/Zustand/MobX**。
- 列表/详情数据走 ProComponents 的 `ProTable.request` + `ProForm`，由组件自管 loading/分页/搜索。
- 跨页面的少量全局状态用 React Context：`src/context/clusterGlobalContext.tsx`、`proxyContext.tsx`、`routerContext.tsx`。
- 全局事件用 `src/utils/gobalEvent.ts`（基于 `eventemitter3`）。
- 用户态走 `src/utils/account.ts`，鉴权刷新走 `src/utils/authorityUtils.ts`。

---

## 4. 路由

- 入口 `src/routes/index.tsx`，使用 `createBrowserRouter`。
- 所有页面通过 `React.lazy` 懒加载。
- 路由树分两类：
  - **集群无关**：`/account/login`、`/Colony`、`/User`、`/Dag`
  - **集群相关**：`/Cluster/:clusterId/{ServiceManage,HostManage,AlarmManage,SystemCenter}`，外层是 `<Proxy />` 容器（`src/pages/Proxy`），内部菜单由 `invokeGenMenuByPattern` 根据是否带 `:clusterId` 动态生成。
- 所有 path 会被前缀 `VUE_APP_PUBLIC_PATH`（见 `src/config/index.tsx`）。base 路径必须和 `vite.config.ts` 中的 `base: /${pkgJson.name}` 一致，即 `/ddh`。

---

## 5. 目录结构

```
src/
├── api/
│   ├── httpApi/        # 按业务域分文件的 API 常量：cluster / host / services / system / upload / user
│   ├── services/       # 业务 API 调用层：api / dataSource / user / index
│   ├── baseUrl.ts      # 根路径计算：开发环境前缀 /dev-mock，命中 vite 代理
│   ├── request.ts      # axios 通用封装（handleParams 转 FormData）
│   ├── interceptors.ts # 401/500 等状态码统一处理
│   └── index.ts        # 对外导出 API 与 intervalTime
├── pages/              # 顶层页面：AlarmManage / Cluster / Colony / Dashboard / HostManage / Login / Proxy / ServiceManage / SystemCenter / User
├── components/
│   ├── Common/         # CommonTable / CommonModal / CommonMonacoEditor / CommonLogModal / CommonTabs / CommonTemplate / CommonBtnList / CommonActionRender / CommonDetails
│   ├── DagModal/       # 全局 DAG 弹窗（暴露 window.invokeShowDagModal）
│   ├── UploadDeployModal/
│   └── UploadDeployConfigModal/
├── routes/             # index.tsx 路由表 + interface.ts
├── context/            # clusterGlobalContext / proxyContext / routerContext
├── hooks/              # useClusterFromParams / useInstanceHooks
├── constants/          # artifactType / clusterType / connectionType / resourceType
├── config/             # 全局配置 + setupTests.ts
├── styles/             # tailwind.css + 全局样式入口
├── utils/              # account / request / authorityUtils / ChunkedUploader / dateTime / formatter / gobalEvent / listUtils / routerUtils / uploadUtils / yamlUtils / md5.worker.js ...
├── assets/
├── App.tsx             # ConfigProvider + RouterProvider 根
└── main.tsx            # 启动入口，先 checkLogin 再 createRoot
```

`public/` 目前只放了 `vite.svg`，无更多静态资源。

---

## 6. 关键约定（来自 AGENTS.md 并经过校对）

### 6.1 代码风格

- 使用 4 空格缩进、句末加分号、字符串双引号、多行结构保留尾逗号。
- 没有 Prettier 配置；改既有文件时**严格遵循该文件的缩进与换行风格**，不要顺手格式化整文件。
- 命名：组件 `PascalCase`、函数/变量 `camelCase`、文件名工具用 camelCase / 组件用 PascalCase、真正的常量 `UPPER_SNAKE_CASE`、布尔变量以 `is/has/should` 开头。

### 6.2 模块导入

- 路径别名 `@` 指向 `./src`（**仅 `vitest.config.ts` 中显式配置过；`vite.config.ts` 当前没有重复声明**，引入新别名时请同步两处）。
- 优先使用具名 ES Module 导入。
- TS 类型必须用 `import type` 或 `import { type X }`（`verbatimModuleSyntax` 开启）。
- lodash 必须使用 `lodash-es`，不要引入 CommonJS 版本。

### 6.3 React 组件

- 函数组件 + hooks，默认导出 `export default ComponentName`。
- 性能敏感处用 `useCallback` / `useMemo`。
- 异步弹窗/弹层用 `asyncHook` 模式：`const showModal = asyncHook(() => import('./Modal/api'))`，调用 `(await showModal()).default({ ...config })`。

### 6.4 API 与提示

- API 常量定义在 `src/api/httpApi/*`，业务调用集中在 `src/api/services/*`，请勿绕过 `axios` 封装直连 fetch。
- 返回值处理统一走 `showMsgAfferRequest`（成功/失败统一弹 message），确认弹窗用 `showComfirmModal`，均位于 `src/utils/util.ts` 与 Common 组件下。
- 401 由 `src/api/interceptors.ts` 触发 `account.clear()` + `invokeRelogin()`，业务代码无需重复处理登录失效。

### 6.5 测试

- 测试文件与源码同目录，命名 `*.test.tsx` / `*.test.ts`。
- 使用 Vitest，`globals: true`（无需 import `describe / it / expect / vi`）。
- mock 用 `vi.mock()` 写在文件顶部；`vi.fn()` 造函数 mock。
- 每个测试 `afterEach(() => cleanup())`。
- 全局 setup：`src/config/setupTests.ts`，自动接入 `@testing-library/jest-dom` 匹配器。
- jsdom 环境，浏览器 API（如 `window.location`、`document.body`）默认可用。

### 6.6 样式

- 优先 Tailwind 原子类；复杂样式用 Antd Token + `ConfigProvider`（已开启 `cssVar`）。
- Tailwind `preflight: false`，所以 Antd 默认样式不会被重置，新写组件不要再用 `@apply` 重置 Antd 元素。

---

## 7. 与后端联调

- 开发环境通过 `vite.config.ts` 中 `server.proxy` 把 `/ddh/dev-mock/**` 反代到目标 API（默认 `http://192.168.2.230:8081/`）。如要换环境，**只改 `vite.config.ts` 中 `target`**，不要把环境写入业务代码。
- `src/api/baseUrl.ts` 根据 `process.env.NODE_ENV === 'development'` 在路径前加 `/dev-mock`，命中上面的代理。
- 后端默认上下文路径 `/ddh`，与 vite `base = /ddh` 严格一一对应，改任一处都要同时改另一处。
- 大文件上传走 `src/utils/ChunkedUploader.ts` + `md5.worker.js`（hash-wasm / spark-md5），分片由 `axios` post，超时设为 24h。

---

## 8. 构建产物与部署

- `pnpm build` 输出到 `dist/`，目录结构由 `vite.config.ts` 决定：HTML 在根、JS/CSS/资产在 `dist/static/`。
- `pnpm deploy` 跑 `node deploy.sh.js`，按 `deploy.config.js` 把 `dist/` 拷到 `../datasophon-api/src/main/resources/front/static/resources/bundle-main`，并把 `dist/index.html` 复制到 `front/views/index.html`（其中 `/static/` 路径会被原样保留）。这条用于本地手工联调，CI 中不需要。
- CI / Maven 打包链路上：`frontend-maven-plugin` 跑 `npm run build` → `maven-resources-plugin` 把 `dist/` 同步到 `${project.parent.basedir}/static` → `datasophon-api` 的 assembly 把 `static/` 打入 `datasophon-manager-*.tar.gz`。
- **绝对不要**把 `dist/`、`node_modules/`、`coverage/` 提交到 git。

---

## 9. 常见踩坑提醒

- 修改路由层级时，请同时检查 `routesMap` / `menuMap` / `invokeGenMenuByPattern` 三个生成结构，菜单与权限均依赖它们。
- 新增 API 时，常量加在 `src/api/httpApi/<domain>.ts`，调用方从 `import { API } from "@/api"` 拿；不要在业务组件中硬编码 URL。
- Antd 6 与 React 19 偶尔需要 `@ant-design/v5-patch-for-react-19`，如遇 hooks API 警告，再打开 `src/main.tsx` 中被注释掉的 import。
- `tsconfig` 关掉了 `strict`，但 `noUnusedLocals/Parameters` 是开的，新增代码不要遗留未使用变量；用 `_` 前缀也不会豁免，请直接删除。
- Tailwind preflight 关闭意味着 `<button>`、`<ul>` 等保留浏览器默认样式，自定义裸 HTML 时请显式补样式或换用 Antd 组件。
- pom.xml 中 `clean` 插件会清空 `${basedir}/dist`，本地改完一定要再 `pnpm build` 才能产出最新产物。
