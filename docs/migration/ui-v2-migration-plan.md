# 计划:datasophon-ui → datasophon-ui-v2 前端迁移(地基 + 登录/Colony 切片,前后端协同)

> 本文件是迁移工作的权威计划文档,跨会话持续更新。每个 Phase 验证通过后更新进度跟踪表(⬜→✅)。

## Context(为什么做这件事)

旧前端 `datasophon-ui` 是手搓的 **Vite + React Router 7 + axios 0.22** 脚手架(运行时函数生成菜单 + 全局 map + 事件驱动重登录 + 双请求封装),维护成本高、与社区脱节。新脚手架 `datasophon-ui-v2` 基于官方 **ant-design-pro 6 / UmiJS Max 4**,配置式路由 / `access` 权限 / 统一 `request` / ProLayout / i18n / Vitest+Biome 开箱即用。

本次把旧业务迁到新脚手架。鉴于体量(10 个页面模块 + DagModal/Monaco/分片上传等重型组件),**本计划只覆盖「框架地基 + 登录/Colony 集群管理垂直切片」**,打通端到端后再按页面组迭代。

### 已确认的决策(来自需求澄清)

1. **范围**:地基(路由/请求/鉴权/布局/构建)+ 登录/Colony 集群管理切片,端到端跑通。其余模块后续按组迭代,各自成计划。
2. **忠实度**:**借机现代化重构**——业务逻辑保持一致,按 ProComponents v3 最佳实践重写(ProTable `request`、ProForm `StepsForm`);清理已声明未用依赖(g6/dagre/elkjs)。
3. **部署**:**暂作独立工程并行开发**,本轮不碰 Maven/assembly/`frontend-maven-plugin`,用 `max dev` + proxy 指向后端。`/ddh` 接管与打包集成留待全部迁完后单独处理。
4. **首切片**:登录 + Colony(集群无关页,天然绕开最难的 `/Cluster/:clusterId/` 动态菜单)。
5. **🔑 适配方向反转**:**让 v2 前端保持 ant-design-pro 标准接口约定,同步改后端 datasophon-api 对齐**——而非把新前端弯去迁就旧后端。范围=**渐进式**:本切片登录 + Colony 所有接口都改后端对齐标准信封 `{success,data,errorCode,errorMessage,showType}` + JSON。前端 `requestErrorConfig` 保持脚手架原生。

### 🚧 硬约束:不能破坏旧 UI

旧 `datasophon-ui` 仍内嵌在 datasophon-api 里运行、依赖现有 `{code,msg,data}` 接口与 `/login`;`server.servlet.context-path=/ddh` 不可改。因此后端改造必须 **加法式、不动现有接口**。

---

## 关键架构决策

|  #  |          决策点           |                                                                    现状                                                                    |                                                                    方案                                                                     |                           理由                           |
|-----|------------------------|------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| D1  | **后端 v2 接口形态**         | 现有 `/ddh/api/**` 返回 `{code,msg,data}`,旧 UI 依赖                                                                                            | **新增 `/ddh/api/v2/**` 一套标准信封接口**,复用现有 Service 层;旧接口原样保留                                                                                   | 不破坏旧 UI,v2 前端保持干净标准                                    |
| D2  | **标准响应信封**             | `Result extends HashMap`(`code/msg/data`)                                                                                                | v2 控制器返回 `{success, data, errorCode, errorMessage, showType}`;经一个 **v2 专属 `ResponseBodyAdvice` + `@RestControllerAdvice`** 统一包装 + 异常转标准信封 | 对齐 scaffold `requestErrorConfig` 的 `errorThrower`,前端零改 |
| D3  | **鉴权机制**               | Cookie session(HttpOnly `sessionId`)+ 非 HttpOnly CSRF cookie(`X-XSRF-TOKEN`),`Authenticator`/`SessionService`/`CsrfTokenInterceptor` 已实现 | **沿用** cookie session + CSRF;v2 登录复用 `Authenticator`/`SessionService`                                                                     | 鉴权链成熟,不重造;非 Bearer token                               |
| D4  | **登录接口**               | `@RequestMapping("/login")` 收 **form 参数** → 种 cookie → `Result`                                                                          | 新增 `POST /ddh/api/v2/login/account` 收 **JSON** `{username,password}` → 复用 `Authenticator` 种 cookie → 返回标准信封含用户信息                          | scaffold LoginForm 提交 JSON;form→JSON 是"采用前端接口"         |
| D5  | **当前用户接口**             | **不存在**(旧前端把 login 响应 userInfo 存 localStorage)                                                                                           | **新增 `GET /ddh/api/v2/currentUser`**,从 session 取登录用户返回标准信封                                                                                | scaffold `getInitialState` 依赖此接口;补齐才能走标准流程             |
| D6  | **登出接口**               | `POST /signOut`(form)                                                                                                                    | 新增 `POST /ddh/api/v2/logout` 复用 `SessionService.signOut`                                                                                  | 对齐 v2 退出流程                                             |
| D7  | **前端请求层**              | scaffold 默认 `{success,...,showType}`                                                                                                     | `requestErrorConfig` 保持原生;仅在 app.tsx `request` 加 `withCredentials:true` + `requestInterceptors` 注入 CSRF 头(非 GET 读 `XSRF-TOKEN` cookie)    | cookie session 必须带 credentials + CSRF                  |
| D8  | **前端 baseURL / proxy** | —                                                                                                                                        | 前端 `baseURL='/ddh/api/v2'`;`config/proxy.ts` 把 `/ddh` 代理到 `http://localhost:8081`,`changeOrigin:true`                                     | context-path 是 `/ddh`,保持一致                             |
| D9  | **路由/菜单**              | 运行时函数 + 全局 map + Proxy 布局                                                                                                                | `config/routes.ts` 配置式;Colony 等集群无关页先落地;`/Cluster/:clusterId/` 动态服务实例菜单**留待后续切片**                                                         | Colony 不依赖动态菜单                                         |
| D10 | **Colony 接口**          | `/ddh/api/cluster/list` 等(FormData,`{code,msg,data}`)                                                                                    | 新增 `/ddh/api/v2/cluster/**`(JSON,标准信封),委托现有 `ClusterService` 实现                                                                           | 渐进式对齐,复用业务逻辑                                           |

---

## 进度跟踪表

| Phase | 端  |                    内容                     | 状态 |                                   验证                                    |
|-------|----|-------------------------------------------|----|-------------------------------------------------------------------------|
| 0     | 双  | 计划落库 + v2 工程地基(proxy/构建/依赖清理/品牌)          | ✅  | 计划在 `docs/migration/`;`npm run dev` 空壳起得来                               |
| 1     | 后端 | v2 标准信封基建(Advice + 异常处理 + v2 包结构)         | ✅  | 任一 v2 接口返回 `{success,data,...}`;`mvnw compile` 零错误                      |
| 2     | 后端 | v2 鉴权接口(login/account、currentUser、logout) | ✅  | 代码完成,编译通过;待后端启动后 curl 验证                                                |
| 3     | 前端 | 请求层 + 鉴权地基(D7/D8)+ getInitialState/access | ✅  | 45 个单测全绿(含 CSRF 注入 4 用例 + 401 跳转);tsc 零错误                               |
| 4     | 前端 | 路由/布局/菜单骨架(D9)+ 登录页切片                     | ✅  | tsc 零错误;45 个单测全绿;i18n colony 键补齐;登录页仅账号密码                               |
| 5     | 双  | Colony 切片:v2 cluster 接口 + 列表/增改/向导/授权     | ✅  | ClusterV2Controller 编译通过;前端 tsc+lint+45 test 全绿;待后端启动后端到端验证             |
| 6     | 双  | 整体验证:lint/tsc/test 全绿 + 浏览器走查             | ✅  | lint/tsc/45tests 全绿;后端核心34tests(WorkerRegistry/CommandClient/AppServer) 全绿;待本地 MySQL+后端启动后浏览器走查 |

> 每个 Phase 验证通过后更新对应行状态(⬜→✅)。

---

## Phase 0 — 计划落库 + v2 工程地基

1. **落库**:把本计划写入 `docs/migration/ui-v2-migration-plan.md`。
2. **代理** `config/proxy.ts`:启用 `dev`,`'/ddh'` → `http://localhost:8081`,`changeOrigin:true`。
3. **构建** `config/config.ts`:`publicPath`/`base` 独立工程阶段保持 `/`(打包集成后续再处理);保留 antd v6 token、locale `zh-CN`。
4. **品牌** `config/defaultSettings.ts`:`title` 已是 DataSophon;替换 alipay 占位 logo(用旧 `datasophon-ui/src/pages/Login/Logo`)。
5. **清理示例**:`config/routes.ts` 移除 admin/dashboard/form/list/profile/result/chatbot 等示例路由(保留 `/user/login`、exception);示例页 Phase 4 收尾删。
6. **依赖**:本切片暂不需要 x6/monaco/分片上传;**不**引入旧版未用的 g6/dagre/elkjs。

**验证**:`npm run dev`(MOCK=none)起得来,空壳无报错。

---

## Phase 1 — 后端 v2 标准信封基建(datasophon-api)

**目标**:建立 v2 接口的统一返回/异常包装,且不影响旧接口。

1. **新建 v2 包**:`com.datasophon.api.controller.v2`,所有 v2 控制器置于此,便于 Advice 按包限定作用域。
2. **标准信封 DTO**:`ApiResponse<T> { boolean success; T data; Integer errorCode; String errorMessage; Integer showType; }`(放 `datasophon-api/src/main/java/com/datasophon/api/dto/ApiResponse.java`)。
3. **`V2ResponseBodyAdvice`**:`@RestControllerAdvice(basePackages="com.datasophon.api.controller.v2")`,把 v2 控制器返回值统一包装成 `ApiResponse`;若返回的是现有 `Result`,做 `{code,msg,data}` → `{success: code==200, data, errorCode: code, errorMessage: msg}` 转换。
4. **v2 异常处理**:`V2ApiExceptionHandler`(`@RestControllerAdvice(basePackages="...controller.v2")`)捕获异常 → `success:false` + `showType=2` + `errorMessage`。
5. **拦截器配置**:确认 `AppConfiguration` 放行 `/api/v2/login/account`(与现有 `/login` 同级放行);`/api/v2/currentUser` 等需登录态的 v2 接口走现有 `LoginHandlerInterceptor` 校验。

**验证**:加一个临时 `GET /ddh/api/v2/ping` 返回标准信封,curl 验证格式;确认旧 `/ddh/api/cluster/list` 返回不变。

---

## Phase 2 — 后端 v2 鉴权接口(datasophon-api)

新建 `LoginV2Controller`(`com.datasophon.api.controller.v2`):

- **`POST /ddh/api/v2/login/account`**:收 JSON `{username, password}`(`@RequestBody` DTO)→ 复用 `Authenticator.authenticate` + `HttpUtils.getClientIpAddress` → 成功则按现有 `LoginController.login` 逻辑种 session cookie + CSRF cookie(`CsrfTokenInterceptor.generateToken`)→ 返回标准信封 `{success:true, data: <用户信息/权限/角色>}`。失败 → `success:false` + `errorMessage`。
- **`GET /ddh/api/v2/currentUser`**:从 session(`Constants.SESSION_USER`,经 `LoginHandlerInterceptor` 注入)取 `UserInfoEntity` → 返回标准信封(映射为前端 `API.CurrentUser`:name/avatar/access/roles/permissions)。未登录由拦截器 401。
- **`POST /ddh/api/v2/logout`**:复用 `SessionService.signOut` + 清 CSRF cookie → 标准信封。

**复用而非重写**:`Authenticator`、`SessionService`、`CsrfTokenInterceptor`、`HttpUtils` 全部沿用;v2 控制器只是"JSON 入参 + 标准信封出参"的薄封装。

**验证**:

```bash
curl -c /tmp/cookiejar -X POST http://localhost:8081/ddh/api/v2/login/account \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
curl -b /tmp/cookiejar http://localhost:8081/ddh/api/v2/currentUser
```

---

## Phase 3 — 前端请求层 + 鉴权地基(datasophon-ui-v2)

1. **`src/app.tsx` `request`**:`baseURL:'/ddh/api/v2'`、`withCredentials:true`(D7)。
2. **`src/requestErrorConfig.ts`**:保持脚手架原生;仅在 `requestInterceptors` 加:非 GET 请求从 `XSRF-TOKEN` cookie 读 token 注入 `X-XSRF-TOKEN` 头(复刻旧 `utils/request.ts`)。
3. **`src/app.tsx` `getInitialState`**:调 `GET /currentUser`(走 baseURL)取用户;失败跳 `/user/login`;login 路径跳过。返回 `{currentUser, settings, fetchUserInfo}`。
4. **`src/access.ts`**:从 `currentUser` 读 roles/permissions 导出 `canAdmin`;Colony 不挂 `access`。
5. **`src/app.tsx` `layout`**:`onPageChange` 未登录跳 login;`avatarProps` 退出调 `POST /logout` + 清态。

**验证**:为 CSRF 注入器写 Vitest 单测;登录后 `request('/cluster/list')` 拿到数据。

---

## Phase 4 — 前端路由/布局骨架 + 登录页切片(datasophon-ui-v2)

1. **`config/routes.ts`**:`/user/login`(`layout:false`)、`/`→redirect `/colony/manage`、`/colony/manage`、`/*`→404。
2. **i18n**:`src/locales/zh-CN/menu.ts` 补 `menu.colony.manage`;en-US 占位。
3. **登录页 `src/pages/user/login/index.tsx`**:基于脚手架 `LoginForm` 改造(只留账户密码 tab);提交调 `POST /login/account`(JSON);成功 `setInitialState` + 跳 `/colony/manage`,失败表单不关。品牌:标题「DataSophon 登录」+ 旧 Logo。

**验证**:`admin`/`admin123` 登录 → 进 ProLayout → 菜单含「集群管理」。

---

## Phase 5 — Colony 集群管理切片(前后端协同,现代化重构)

对照旧 `datasophon-ui/src/pages/Colony/` 与 `api/httpApi/cluster.ts`。

**后端**:新建 `ClusterV2Controller`(`controller.v2`),暴露 JSON 版集群接口,**委托现有 `ClusterServiceImpl` 等 Service**,经 v2 Advice 出标准信封:
- `POST /ddh/api/v2/cluster/list`
- `POST /ddh/api/v2/cluster/save`
- `POST /ddh/api/v2/cluster/update`
- `POST /ddh/api/v2/cluster/delete`
- `GET /ddh/api/v2/frame/list`
- `POST /ddh/api/v2/cluster/user/saveClusterManager`
- `POST /ddh/api/v2/cluster/k8sConfig/saveOrUpdateConfig`
- `POST /ddh/api/v2/cluster/k8sConfig/testConnection`
- `GET /ddh/api/v2/cluster/group/list`

**前端**(按子步骤推进):
- **5.1 列表** `src/pages/Colony/Manage/index.tsx`:`ProCard.Group` 卡片网格,数据 `POST /cluster/list`;末尾「新增集群」卡。迁移旧 `Card` 的操作(进入/编辑/删除/授权/配置)。
- **5.2 新建/编辑** `components/BuildOrEditModal`:ProForm `ModalForm`,集群名/框架版本/类型(物理/K8s);`/frame/list` 填下拉;提交 `save`/`update`。
- **5.3 物理集群向导** `components/ConfigModal`(8 步):重构为 ProForm **`StepsForm`**,Step1–8 各一 `StepForm`;Step5 若涉及 `sm-crypto`/`js-yaml`,一并迁入。
- **5.4 K8s 配置** `components/ConfigModalK8s`:ModalForm + k8sConfig 接口;kubeconfig 编辑依赖 Monaco——**可标记延后,物理集群优先打通**。
- **5.5 授权** `components/AuthModal`:ModalForm + `saveClusterManager` + 用户/组列表。

**验证**:登录 → 列表加载 → 新建物理集群(走向导)→ 提交成功 → 列表刷新出现新集群 → 编辑/删除/授权可用。

---

## Phase 6 — 整体验证

- 前端:`npm run lint`(Biome+tsc)无错;`npm run test` 全绿。
- 后端:`./mvnw -pl datasophon-api test`(JDK17,`-s ~/.m2/setting.xml`)绿;**旧接口回归**——旧 `datasophon-ui`(:5180)仍能登录与列集群。
- 浏览器人工走查 v2 端到端主路径。

---

## 复用资产

**后端复用(不重写)**:`Authenticator`、`SessionService`、`CsrfTokenInterceptor`、`HttpUtils`、`ClusterServiceImpl` 等现有 Service 层。
**前端参考旧版**:`datasophon-ui/src/utils/request.ts`(CSRF 注入)、`api/httpApi/cluster.ts`(接口清单)、`constants/clusterType`、`Login/Logo`。
**v2 已备好**:ProLayout、`getInitialState`/`access`、`request` 插件、i18n、Vitest+Biome。

---

## 风险与后续切片

- **最高风险已避开**:本切片是集群无关页,不碰 `/Cluster/:clusterId/` 动态服务实例菜单、DagModal(x6)、CommonMonacoEditor、分片上传 worker。
- **本切片内次高风险**:8 步向导→StepsForm 工作量大;K8s 配置依赖 Monaco——建议先打通物理集群主路径。
- **后续切片建议顺序**:① 集群内布局 + 动态服务实例菜单(Proxy 重构,最难)→ ② ServiceManage/Instance(Monaco/Helm)→ ③ HostManage → ④ AlarmManage+SystemCenter+User → ⑤ DagModal(x6)→ ⑥ 分片上传+UploadDeploy → ⑦ Maven/assembly 打包集成 + `/ddh` 接管 + 旧 UI 退场。

---

## 验证方法(端到端)

1. **启动后端**:`JAVA_HOME=$JH17 ./mvnw -pl datasophon-api spring-boot:run -s ~/.m2/setting.xml`(`:8081`/`/ddh`,MySQL `:3306`)。
2. **启动前端**:`cd datasophon-ui-v2 && npm run dev`(MOCK=none),proxy 指向 `:8081`。
3. **登录**:`admin`/`admin123` → 跳 `/colony/manage`。
4. **Colony 主路径**:列表加载 → 新建物理集群(向导)→ 提交 → 列表刷新 → 编辑/删除/授权可用。
5. **旧 UI 回归**:旧 `datasophon-ui`(:5180)仍能登录列集群。
6. **质量门禁**:前端 `npm run lint && npm run test`;后端 `./mvnw -pl datasophon-api test` 全绿。

