# 前端第一、二轮优化交接

## 续接状态：2026-09-20 09:30（Asia/Shanghai）

继续在 `/Users/pro/.codex/worktrees/ui-first-round/datasophon`、`codex/ui-first-round` 工作，未提交、推送、合并或部署。以下为最新增量，9 月 18 日结果保留为历史证据。

- 独立审查发现并修复两项问题：服务详情异步查询在切群、卸载或再次点击后仍可能跳回旧目标；概要接口失败会把成功但为空的告警列表误标为失败。
- 服务跳转增加请求序号和生命周期失效保护；`useClusterSummary` 分别暴露 `summaryFailed` / `alertsFailed`，两个面板只使用对应失败标记，顶部继续汇总错误。
- Dashboard 回归先红：5 failed / 3 passed（`.scratch/ui-second-round/review-red.log`）；修复后 Dashboard 与 summary hook 共 2 文件、15/15 passed（`review-green.log`）。另复跑此前最后修改的 3 文件、8/8 passed。
- 发现并修复 `/ddh` 部署下登录页无限 401 重定向：`app.tsx` 原先只识别 `/user/login`，生产浏览器实际路径为 `/ddh/user/login`。新增路径归一化和回归测试，修复前 `login-red.log` 为 1 failed / 6 passed，修复后 `src/app.test.tsx` 7/7 passed。
- 修复后的前端全量单元测试：96 文件，517/517 passed（`.scratch/ui-second-round/tests-final-20260920.log`）。直接运行项目已安装的 `tsc --noEmit` 通过；修改文件 Biome check、`git diff --check` 通过。`max build` 构建通过，仍有包体较大提示（`login-build-max.log`）。
- 修复后再次独立只读审查未发现高置信问题，`git diff --check` 通过。
- `npx antd lint ./src` 退出 0，详情保留在 `.scratch/ui-second-round/review-antd.log`。
- 用户允许后复用 ego-browser TaskSpace **1** 完成验收：生产预览代理现场后端，账号登录成功进入 `/ddh/cluster/1/overview`，5 主机、8 服务和真实指标可见。
- 390px 视口截图和 DOM 验收通过：页面宽度 390、body 宽度 390；两张业务表保留 640px 内容宽度并可横向滚动；长名称均有 `title`。截图：`.scratch/ui-second-round/dashboard-mobile-20260920.png`。
- 测试浏览器阻断 OTel 指标请求后，页面显示“监控指标查询失败”，CPU/网络面板显示“指标查询失败，请重试”，页面 toast 数量为 0；解除阻断并刷新后失败提示消失、3 个 canvas 恢复。截图：`dashboard-mobile-failure-20260920.png`、`dashboard-mobile-recovered-20260920.png`。
- 已清空网络阻断并关闭 Network 域，将尺寸恢复为 1440px；TaskSpace 1 已正常 finish，临时预览已停止且 8800 端口无监听。截图级视觉验收现已完成。
- 当前仍未提交、推送、合并或部署；需要提交时另行确认完整文件清单（超过 10 文件），精确暂存。

## 历史状态：2026-09-18 21:20（Asia/Shanghai）

第一轮和第二轮代码已完成并通过以下验证，所有改动仍未提交、未推送、未合并。主工作区现有改动未被修改。

### 第二轮新增

- 指标面板区分加载中、查询失败和成功但无数据；刷新期间已有图表保持挂载，避免交互状态被重置。
- 集群概览的 CPU/网络面板分别传递失败状态；失败时保留汇总告警和对应面板反馈。
- 概览的 OTel 批量请求启用页面级错误汇总，避免多个指标请求失败时重复弹出全局 toast；普通请求默认行为不变，401 与业务重定向仍跳转登录。
- 告警和服务健康表在窄屏下保留 640px 横向内容宽度；服务、告警目标和主机名称可通过 `title` 查看完整文本，既有详情跳转保留。

### 第二轮验证

| 检查 | 结果 | 日志 |
|---|---|---|
| 第二轮红灯基线 | 请求静默 opt-in 修复前 4 文件，5 failed / 31 passed | `.scratch/ui-second-round/request-red.log` |
| 前端全量单元测试 | 96 文件，509/509 passed | `.scratch/ui-second-round/tests.log` |
| `npm run tsc` | 通过 | 终端复跑；`npm run lint` 初次日志曾包含测试 mock 类型错误，已修复后单独 tsc 通过 |
| `npm run biome:lint` | 退出 0；1 个失效技能软链 warning、8 条既有 style info | 终端 |
| `npx antd lint ./src` | 退出 0；12 deprecated、74 usage、0 a11y、0 performance | `.scratch/ui-second-round/antd.log` |
| `npm run build` | 通过；仍有包体较大提示 | `.scratch/ui-second-round/build-final.log` |
| `git diff --check` | 通过 | 终端 |

### 第二轮浏览器验收

复用 ego-browser TaskSpace 2 和本地生产预览 `127.0.0.1:8800`，API 代理到现场测试后端，未部署服务器。

- 390px 视口下页面总宽度仍为 390px；两张表的可视宽度约 323px、内容宽度 640px、横向滚动开启，没有页面级横向溢出；长名称均有 `title`。
- 临时阻断 OTel 指标请求后，页面显示汇总“监控指标查询失败”，CPU/网络图表显示“指标查询失败，请重试”，同时页面 toast 数量为 0。
- 已发出解除阻断和刷新命令，但未取得该调用的完成输出，不能据此确认第二轮故障恢复。截图接口本轮未产出可审查文件，因此不声明截图级视觉验收通过。
- 临时预览已停止；此前“尺寸模拟已解除”的记录缺少执行证据，撤回该断言。9 月 20 日确认旧 TaskSpace 2 已不存在。

### 已实现

- 集群布局：窄屏自动收起侧栏、手动展开/收起、可返回的真实面包屑、服务深层页面高亮。
- 集群状态：集群加载失败可重试；服务刷新失败保留旧结果并说明最近更新时间；隐藏页面暂停请求、避免慢请求重叠，忽略切群和卸载后的旧响应。
- 概览：透传指标查询失败及失败面板，显示最近完整查询成功时间；跨集群/时间范围屏蔽旧指标、清空跨群概要；告警与服务健康提前展示。
- 概览直达：服务名称按实际实例 ID 跳转；告警目标已有 serviceInstanceId 时直接跳转。主机没有独立详情/URL 筛选契约，因此仍显示文本。
- 刷新：移除概览和时序图因时间更新而重新挂载的 key；共享工具栏在隐藏时暂停自动刷新、返回时刷新一次；修复 StrictMode 下 updater 副作用可能重复刷新。
- DAG：加载/轮询失败有提示与读取重试，保留已绘制的图；请求序号防止卸载/过期响应绘图和继续排轮询。读取重试不调用重新部署接口。

### 验证结果

以下命令均在 worktree 的 `datasophon-ui-v2/`，使用 `PATH=/Users/pro/.nvm/versions/node/v22.22.2/bin:$PATH`：

| 检查 | 结果 | 日志（仓库根目录相对路径） |
|---|---|---|
| 刷新/DAG/图表红灯基线 | 3 文件，6 failed / 1 passed；修复前验证 | `.scratch/ui-first-round/refresh-dag-red.log` |
| 对应修复后定向测试 | 3 文件，7/7 passed | `.scratch/ui-first-round/refresh-dag-green.log` |
| Dashboard 定向测试 | 5 文件，20/20 passed | `.scratch/ui-first-round/dashboard-tests.log` |
| Layout 定向测试 | 10/10 passed | 同时包含在全量结果中 |
| `npm test` | 95 文件，499/499 passed | `.scratch/ui-first-round/all-tests.log` |
| `npm run lint`（含 tsc） | 退出 0；1 个失效技能软链 warning、8 条既有风格 info | `.scratch/ui-first-round/lint.log` |
| `npx antd lint ./src` | 退出 0；12 deprecated、74 usage、0 a11y；静态反馈 API 等存量提示未扩展清理 | `.scratch/ui-first-round/antd-lint.log` |
| `npm run build` | 构建成功；仍有包体较大提示 | `.scratch/ui-first-round/build.log` |
| 修改文件定向 Biome / `git diff --check` | 通过 | `.scratch/ui-first-round/format.log` / 终端 |

独立只读审查共享刷新、图表和 DAG 变更未发现高置信度新增问题；Layout / Dashboard 已做行为回归与自查。未运行后端测试，本轮没有后端修改。

### 实际浏览器验证与边界

本机预览使用当前 worktree 的生产 dist，临时监听 `127.0.0.1:8800`，API 代理到 `192.168.10.131:8080`；没有部署到服务器。

- 成功登录，集群 1 概览显示 5 主机、8 服务及真实告警；最新告警/服务健康位于资源趋势前面，显示最近完整查询成功时间。
- DOM/布局测量：735px 窄屏时侧栏自动收起（仅剩 1px 边框），展开按钮 aria-expanded=false；手动展开成功；1440px 下侧栏宽度 216px。
- 点击概览中的 Doris，跳转实际路径 `/ddh/cluster/1/service/10`，面包屑显示 Doris；点击集群面包屑可回到概览。
- 仅在测试浏览器使用 CDP 阻断指标 URL，页面显示“监控指标查询失败”和受影响指标，最近成功时间保留；解除阻断并刷新后失败提示消失，3 个 canvas 恢复，成功时间更新至 21:02:39。未改变现场 Collector 或后端服务。
- 截图接口 `Page.captureScreenshot` 再次超时，未获得可审查截图，不能声明截图级视觉验收通过。DAG 失败重试、深层路由高亮和后台轮询边界主要由自动化测试验证。
- 第一轮曾观察到阻断指标时全局请求逐请求弹 toast；第二轮已为集群概览增加页面级静默 opt-in，第二轮浏览器实测 toast 数量为 0，其他页面默认策略保持不变。
- 测试浏览器阻断和尺寸模拟均已解除，任务浏览器已关闭；临时预览在交付时停止。

### 后续入口

查看本分支 diff 并按需要补充截图级视觉验收；需要提交时先明确审核全部文件清单（已超过 10 文件），按本地规则精确暂存。无需在没有新改动时重复全量测试。临时预览脚本、测试日志在 `.scratch/ui-first-round/`（Git 忽略），凭据不在交接文件或预览脚本中。

---

## 以下为下午暂停时的历史快照（已被上方最新状态取代）

更新时间：2026-09-18 16:50（Asia/Shanghai）
工作区：`/Users/pro/.codex/worktrees/ui-first-round/datasophon`
分支：`codex/ui-first-round`
主工作区：`/Users/pro/IdeaProjects/datasophon`（保留用户现有未提交文件，未修改）

## 目标

用户要求“新建 worktrees 进行第一轮优化”。本轮范围来自上一轮评估：

1. 区分监控查询失败、无数据和过期数据。
2. 集群布局增加响应式侧栏、真实导航上下文、深层路由高亮。
3. 集群概览支持异常/服务直达，并避免刷新时整页重挂载。
4. DAG 加载/轮询失败显示页面反馈。
5. 自动刷新在后台标签页暂停，返回时刷新一次。

## 已完成的准备工作

- 已通过 Codex worktree 创建隔离目录和分支：`codex/ui-first-round`。
- 已复制 `datasophon-ui-v2/node_modules` 到 worktree，避免重新安装依赖。
- 已读取根目录 `CLAUDE.local.md`、模块 `datasophon-ui-v2/CLAUDE.md` 和 ponytail 规则。
- 修改 antd 前已在原 UI 目录运行 `npx antd info Alert`、`npx antd info Button`。
- 未触碰主工作区用户已有的文档和 `datasophon-ui-v2/config/proxy.ts` 等本机联调改动。

## 当前未提交改动

以下改动已由子代理在 worktree 中产生，但尚未经过主代理审查，不能直接提交：

- `datasophon-ui-v2/src/pages/Cluster/Layout/index.tsx`
- `datasophon-ui-v2/src/pages/Cluster/Layout/index.test.tsx`
- `datasophon-ui-v2/src/pages/Cluster/Dashboard/index.tsx`
- `datasophon-ui-v2/src/pages/Cluster/Dashboard/hooks/useClusterOtelPanels.ts`
- `datasophon-ui-v2/src/pages/Cluster/Dashboard/hooks/useClusterOtelPanels.test.ts`
- `datasophon-ui-v2/src/pages/Cluster/Dashboard/panels/RecentAlertsPanel.tsx`
- `datasophon-ui-v2/src/pages/Cluster/Dashboard/panels/ServiceHealthPanel.tsx`
- 新增 `datasophon-ui-v2/src/pages/Cluster/DagGraph/index.test.tsx`
- 新增 `datasophon-ui-v2/src/pages/monitor/_shared/DashboardToolbar.test.tsx`
- 新增 `datasophon-ui-v2/src/pages/monitor/_shared/panels/TimeSeriesPanel.lifecycle.test.tsx`

本交接文档本身也位于 worktree 的 `docs/`，是否纳入最终提交需晚上复核。

## 已有验证证据

先写了共享刷新和图表生命周期回归测试，基线确实失败：

- `npm test -- src/pages/monitor/_shared/DashboardToolbar.test.tsx src/pages/monitor/_shared/panels/TimeSeriesPanel.lifecycle.test.tsx`
  - 结果：2 个测试文件失败，3 个测试失败、1 个通过。
  - 日志：`/tmp/ui-first-refresh-red.log`。
  - 失败原因符合预期：后台仍刷新、StrictMode/生命周期行为未满足，图表 `key` 导致交互状态被重置。
- `npm test -- src/pages/Cluster/DagGraph/index.test.tsx`
  - 结果：失败基线日志在 `/tmp/ui-first-dag-red.log`；实现尚未加入页面失败提示和卸载保护。

注意：当前测试文件中有一处 DAG 失败测试仍需复核测试写法（fake timers 与异步渲染交互，以及 `dagStatus` 动态 import）；不能把它当成生产缺陷证据，先修测试使其稳定红，再改实现。

## 晚上恢复顺序

1. 先执行 `git diff` 审查 Layout 和 Dashboard 子代理改动，删掉超出本轮范围的代码；确认没有把旧集群数据显示到新 `clusterId`。
2. 阅读并修正新增测试，使失败原因单一、断言行为而非 DOM 形状。
3. 修共享 `DashboardToolbar`：监听 `visibilitychange`，隐藏时不触发定时刷新，重新可见时触发一次；保证清理 interval/listener，避免 StrictMode 重复回调。
4. 修 `TimeSeriesPanel`：删除仅用于刷新数据的 `key`，用数据更新而不是整棵图表重新挂载；运行生命周期测试。
5. 修 `DagGraph`：增加 `loadError`/首次与轮询失败状态，轮询失败保留图形并显示“当前为上次成功结果”；增加手动重试；增加请求序号或 cancelled guard，防止卸载后绘图/重新排轮询。
6. 依次运行定向测试：Layout、Dashboard、Toolbar、TimeSeries、DAG；再运行 `npm run tsc`、`npm run biome:lint` 和 `npx antd lint ./src`。
7. 只对本轮实际修改文件执行 Biome 格式化；检查 `git diff --check` 和 `git status`。
8. 做一次登录后浏览器检查。当前 ego-browser 已到 `http://192.168.10.131:8080/ddh` 登录页，测试账号未填写；此前截图调用超时，所以没有真实页面验收证据。
9. 通过代码审查后再决定是否提交。当前没有 commit、push、PR，也没有把 worktree 合并回主工作区。

## 现场与环境边界

- `deploy/deployment-standalone-doris.md` 的当前章节记录 Doris 4.1.3 升级曾处于 `FAILED`，不要把它当作本轮前端测试通过证据。
- 该文档同时记录阶段 A 的五节点环境和 OTel/Doris 数据链路；概览容量优化后续仍需现场验证，特别是系统盘与 `/data` 分离显示。
- 进程检查未发现本轮 worktree 的 npm/vitest 后台任务。另有其他目录的 SeaTunnel 长耗时构建进程，不属于本轮，不要终止。

## 暂停点

现在停止业务修改和长时间验证。代理因额度耗尽已结束，当前只保留 worktree 中的未提交改动和上述测试日志。恢复时从第 1 步开始，不要重置 worktree，也不要覆盖主工作区文件。
