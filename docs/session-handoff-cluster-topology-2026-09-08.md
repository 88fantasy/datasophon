# 集群部署拓扑交接（更新至 2026-09-10）

## 范围与用户确认

- 工作分支：`feat/cluster-topology`。9 月 8 日基础提交为 `df72d35f`；9 月 9–10 日续作随本次提交交付，未推送；提交号可用 `git log -1 -- docs/session-handoff-cluster-topology-2026-09-08.md` 查询。
- 用户确认：不关联物理集群，新建独立 K8s 集群接管远端环境；全景中的 Kubernetes 子集群展示节点，点击 Kubernetes 集群后展示 Service / CR，不展示 Pod。
- 联调使用本地 Datasophon、Docker 中的 MySQL / Nexus，以及 `/Users/pro/.kube/config.bjsy` 能连接的远端集群。
- 入口为集群管理列表的“部署拓扑”，新标签页打开 `/ddh/cluster/:clusterId/topology`，独立全屏页面。

## 当前行为

- 支持 3D、2.5D、2D、明暗主题、搜索、资源详情、视角重置及每五分钟刷新。WebGL 失败降级到 SVG 平面视图；刷新失败保留上次快照并显示错误。
- 全景按前置区、Kubernetes、Doris、VM、其他分区，直接显示服务器 IP；空分区隐藏。物理服务器点击后展示中间件及端口，面包屑可返回；模式和主题切换保留当前层级。
- 物理主机由 `nodeLabel` 决定唯一归属：`edge` / `kubernetes` / `doris` / `vm`，兼容对应中文名称；未配置或自定义标签归其他。
- 独立 `archType=k8s` 集群复用主机列表接口读取 Node，以节点名称作为唯一身份，避免合成整数 ID 碰撞；全景展示 IP，Node 点击只打开详情。`hostState=1` 对应 Ready，`2` 对应 NotReady 并显示告警。
- 点击 Kubernetes 分区展示当前集群已登记实例对应的 Service / CR；不请求或展示 Pod，不把 Service / CR 挂到某个 Node 下，不伪造部署关系或通信流量。
- CR 使用登记名称，登记成功不解释为运行健康；普通 Service 展示实际名称、命名空间、IP、端口、NodePort 和目标端口。
- Service 读取兼容旧版 Helm 的 `release` 标签；额外查询仅命中缺少 `app.kubernetes.io/instance` 标签的资源，仍限定命名空间和 `managed-by=Helm`，避免重复及跨 release 归属。CR 查询保持原行为；拓扑不调用 CR 的通用 Service 资源查询。
- 物理集群中的 Kubernetes 类别主机仍隐藏并显示边界提示，不自动关联其他 clusterId。用户已选择独立接管，无需再询问关联规则。

## 本次文件职责

- `datasophon-ui-v2/src/pages/Cluster/Topology/topologyData.ts`：Node 分页加载、身份和状态映射；保留原有 Service / CR 加载及错误提示。
- `topologyHierarchy.ts`：全景显示 Node，Kubernetes 分区显示 Service / CR。
- `index.tsx`：Node 仅详情、分区节点数、K8s 操作提示；复用现有三种视图。
- `topologyData.test.ts` / `index.test.tsx`：Node 身份、状态、错误、下钻与模式切换回归。
- `datasophon-api/src/main/java/com/datasophon/api/service/k8s/impl/K8sServiceImpl.java`：仅 `listServices` 增加旧 Helm 标签读取，不修改通用 selector 或其他资源/写入路径。
- `datasophon-api/src/test/java/com/datasophon/api/service/k8s/impl/K8sServiceImplTest.java`：新旧 Helm Service 并存与 CR 查询兼容性验证。
- 场景模型新增服务顶部图标及纹理释放；布局和标签弹窗未改动，没有数据库 schema 或依赖变更。

## 本地接管与真实接口证据

- 新建集群：`17 / 北京三医 K8s 拓扑联调`，代号 `bjsy_topology_20260909`，框架 `datacluster-k8s`，模式 `IMPORTED`。已保存指定 kubeconfig 并验证连接；未复用已有集群。
- 扫描自动匹配并登记 12 个实例：11 个 Helm release + 1 个 Doris 存算分离 CR；扫描 `pending / missing / failedCrds` 均为空。只写本地登记数据，未向远端安装 Agent / Collector 或修改工作负载。
- 最新真实 API 返回 13 个 Node、22 个 Service、1 个 CR；`192.168.201.31` 为 NotReady，其余节点 Ready。
- `verify-live.py` 已逐一核对 Node 名称、IP、Ready 状态，全部展示 Service 的命名空间、名称、ClusterIP、端口，以及 CR 身份，与同期 `kubectl` 结果一致。
- Elasticsearch 的 `elasticsearch-master` 与 `elasticsearch-master-headless`（9200/9300）原先因旧 Helm 标签漏查，修复后已在真实 API 中返回。
- 仍有 1 项可见提示：`doris/fdb-cluster` 对应的 `fdb-operator` 没有匹配 Helm 标签的 Service。远端确有 `fdb-cluster` Service，但标签为 `foundationdb.org/fdb-cluster-name`；未凭同名把 Operator 生成资源认定为 Helm release 直属资源。
- 未配置本次新集群的 Doris 指标数据源：12 个实例登记结果均为 `scraped=false`、`metricsJob=null`。本次验证拓扑和接管，不代表监控已接入。

## 验证结果与截图边界

- 前端拓扑测试：3 个文件、26 项通过。新增行为实施前的基线记录为 4 失败 / 18 通过；不是把原实现的空节点结果作为成功。
- 后端指定测试：`K8sServiceImplTest` 2 项、`K8sMetricsJobProbeServiceTest` 10 项，共 12 项通过、0 跳过。旧 Helm 测试实施前 1 失败 / 1 通过。
- `npm run lint`、`npm run build`、拓扑 `antd lint` 通过；保留既有 8 项 Biome info 和 bundle size 提示。后端仅格式化本次两个 Java 文件。
- 前端 5 个文件已完成独立只读审查，未发现高置信问题；后端增加的读取分支已通过定向测试和真实 API 核对。未执行全仓库测试。
- 9 月 9 日中午曾以生产构建和真实 API 完成浏览器检查：全景资源索引 13 个 Node，NotReady 详情正确；点击 Kubernetes 分区后显示 Service / CR。保存并目视检查了 `live-k8s-overview-3d.png`，该视口下 13 个 IP 标签均可见。
- 上述截图早于 Elasticsearch 标签修复及最终操作提示调整，显示 20 Service + 1 CR，不能当作最新 22 Service + 1 CR 的截图。
- 9 月 10 日用户确认旧工作区是其主动关闭，并授权重建。已在新工作区完成最新生产构建 + 真实 API 的 3D / 2.5D / 2D、明暗主题、节点详情、Service / CR 详情、模式切换保留选择、手动刷新、3D 滚轮缩放及重置验收；未重复创建集群或接管实例。
- 最新浏览器显示 13 个 Node、22 个 Service + 1 个 CR；Elasticsearch 详情含 9200/9300，Doris CR 详情使用登记名称、状态仍为未知。手动刷新后仍为 23 个服务资源，唯一提示仍为 FDB 归属边界；今日浏览器请求日志中 Service 资源请求 22 次（初载 + 刷新），Pod API 请求 0 次。
- 已保存并目视检查截图（均在 `.scratch/cluster-topology/`）：`final-overview-3d-20260910.png`、`final-services-3d-20260910.png`、`final-services-cr-2d-20260910.png`、`final-services-25d-dark-20260910.png`；结构化检查见 `final-browser-evidence-20260910.json`。
- 2550×1279 视口下全景 13 个节点 IP 标签全部可见。服务区 3D 默认视角因避碰显示 20 个资源标签（另有 1 个分区标签），另外 3 个资源标签隐藏；23 个模型及资源索引均保留，2D 可完整浏览。此处是现有标签避碰行为，不能宣称 3D 默认视角全部资源名同时可见。
- 此前 2D CR 点击因导航与画布同名按钮导致歧义，本次限定 `[aria-label="资源列表"]` 后操作成功。最终截图和本轮缩放验收已补齐；全空场景仍以已有自动测试为证，未在真实远端制造空集群。
- 物理集群真实标签分配及 YARN 同步仍未联调，本次未改动该流程。

## 本地启动与继续方式

- 前端：`http://127.0.0.1:8000/ddh/cluster/17/topology`；本次 `local-ui.cjs` 使用生产 `dist`，真实 API 转发到本地 `8080`，未改用户的 `config/proxy.ts`。
- 后端：`http://127.0.0.1:8080/ddh/api/v2`。临时 `LocalBackend.java` / `start-backend.sh` 禁用 migration、启动元数据同步及 `@Scheduled` 巡检，保留 HTTP 业务路径；这不是正常生产启动验收。
- 续作期间 Docker 曾关闭，已重新启动 Docker 和原有 `new-mysql`、`nexus` 容器后完成最新 API 核对。
- `.scratch/cluster-topology/` 被忽略，不随提交。含启动脚本、`verify-live.py`、测试/构建日志、`live-verification.json`、`api-resources.json`、`live-scan.json`、`live-register.json`、`live-nodes.json`、`live-services.json`、`live-cr.json`、早期浏览器记录和截图。
- 先检查 `git status` 和端口是否仍在监听。用户已有 `config/proxy.ts`、血缘、工作流、监控文档不得清理、覆盖或顺带提交；全仓库 diff check 的血缘文档 EOF 警告是既有改动，本次范围检查通过。

前端命令（在 `datasophon-ui-v2` 中，Node 22）：

```bash
export PATH=/Users/pro/.nvm/versions/node/v22.22.2/bin:$PATH
npx --no-install vitest run src/pages/Cluster/Topology
npm run lint
npm run build
npx --no-install antd lint ./src/pages/Cluster/Topology
```

后端定向测试（仓库根目录）：

```bash
JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home \
./mvnw -s ~/.m2/setting.xml \
  -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api \
  -Dskip.installnodenpm -Dskip.npm \
  -Dtest=K8sServiceImplTest,K8sMetricsJobProbeServiceTest -DfailIfNoTests=false test
```

### 2026-09-10 服务模型顶部图标

- 3D / 2.5D 服务模型顶部改为使用资源索引同源的 `serviceIconFor(serviceName)`，替换数据库圆环和容器六边形通用徽标；主机机柜保持原样。
- 同图标共享纹理和材质，图片加载完成触发按需绘制，场景销毁释放纹理。
- 拓扑测试 27/27、`npm run lint`（8 条既有 info）、生产构建和本轮三文件 `git diff --check` 通过。
- 浏览器加载真实集群 17 的 23 项服务成功，未出现三维降级。截图两次及底层 CDP 截图一次均超时，自动截图验收未完成；随后用户明确反馈“验收通过”，人工验收通过。
- 用户已授权提交本次拓扑代码及验收记录；不推送，无关用户文件保留。
