# 集群部署拓扑交接（2026-09-08）

## 范围与入口

- 工作分支：`feat/cluster-topology`。本交接文档随本次代码提交；提交号可用 `git log -1 -- docs/session-handoff-cluster-topology-2026-09-08.md` 查询。未推送。
- 集群管理列表增加“部署拓扑”入口，新标签页打开 `/ddh/cluster/:clusterId/topology`，独立全屏页面。
- 实现在 `datasophon-ui-v2/src/pages/Cluster/Topology/`；复用现有 cluster、host、service、k8s API，无后端及数据库改动。

## 已实现的最终行为

- 支持 3D、2.5D、2D、明暗主题、搜索、资源详情、视角重置及每五分钟刷新。WebGL 失败可降级到 SVG 平面视图；刷新失败保留上次快照并显示错误。
- 全景按前置区、Kubernetes、Doris、VM、其他分区，分区内直接显示服务器 IP；点击分区浏览服务器，点击服务器进入中间件及端口。面包屑可返回，模式和主题切换保留当前层级。
- 没有内容的分区同时从导航及画布隐藏；刷新后当前分区变空返回全景。Doris 缺席时 VM 仍正常排列，全空布局保持有限尺寸。
- `nodeLabel` 决定服务器唯一归属：`edge` / `kubernetes` / `doris` / `vm`，兼容对应中文名称；未配置或自定义标签归其他。主机标签弹窗提供内置选项，提交时创建缺失标签并复用已有标签，业务错误可见。
- 已登记的 `archType=k8s` 集群展示 Service / CR，不请求 Node / Pod。CR 采用登记名称，未将安装成功解释为运行健康。
- 连线仅表示已知部署归属，不伪造真实通信流量。

## 文件职责

- `topologyData.ts`：接口加载、分页与错误检查、IP/端口、标签分类、Service / CR 数据。
- `topologyHierarchy.ts`：全景、子集群、服务器内部的可见节点。
- `topologyLayout.ts`：三种模式共用布局、空分区过滤及位置计算。
- `TopologyScene.tsx` / `topologyModels.ts`：Three.js 模型、相机、拾取、标签避让、资源释放。
- `TopologyPlan.tsx`：SVG / D3 平面浏览。
- `index.tsx` / `index.module.css`：导航、搜索、下钻、刷新及主题。
- `utils/hostCategories.ts` 与 `AssignLabelModal.tsx`：内置类别及标签分配。

## 验证与限制

- 提交前拓扑及标签弹窗测试共 28 项通过（4 个文件），覆盖数据、布局、下钻、刷新、空分区、WebGL 降级及标签分配。
- `npm run lint`、`npm run build` 已通过；现有 Biome info 和 bundle size 提示保留。
- 模拟 API + 生产构建浏览器检查：2D 全景 9 台服务器 IP；点击 `10.80.0.13` 展示 Doris 9030 / ZooKeeper 2181；独立 K8s 场景曾验证 1 CR + 10 Service。最新隐藏空分区改动已由 DOM 检查确认导航、3D、2D 均移除空 Kubernetes 分区。
- 最新 `Page.captureScreenshot` 超时，没有隐藏空分区版本截图；较早的缩放输入也曾遇到 CDP 超时。不能将这些步骤计为通过。未做真实物理集群、YARN 或 Kubernetes API 联调。
- 3D 默认视角存在标签碰撞避让：此前 9 台服务器中 8 个 IP 标签可见，模型与侧栏索引全部保留；高密度视图、缩放及最终截图仍需补验。
- 本机 `.scratch/cluster-topology/`（忽略目录，不随提交）保存 `qa-server.cjs`、逐轮 `acceptance.md` 和截图。历史记录可能描述已被后续修订替换的行为，以本文及最新记录为准。

## 未完成事项与继续方式

1. **物理集群到 Kubernetes 集群关联尚未确定。** 物理集群中 `nodeLabel=kubernetes` 的服务器隐藏，当前没有关联字段可取得其内部 Service / CR；页面显示警告，空分区随之隐藏。曾提出下钻时选择已登记 K8s 集群的交互，但尚未获用户确认，不能擅自跨 clusterId 拼接。继续前先明确关联规则。
2. 标签 API 原有行为会同步 YARN 分区，弹窗已提示；真实标签分配、YARN 同步及失败情况仍待联调。
3. 补最终截图与真实集群验收，特别是 3D 标签密度、K8s Service / CR 及全空场景。

## 本地验证命令

在 `datasophon-ui-v2` 中使用 Node 22：

```bash
export PATH=/Users/pro/.nvm/versions/node/v22.22.2/bin:$PATH
npx --no-install vitest run src/pages/Cluster/Topology src/pages/Cluster/HostManage/components/AssignLabelModal.test.tsx
npm run lint
npm run build
npx --no-install antd lint ./src/pages/Cluster/Topology ./src/pages/Cluster/HostManage/components/AssignLabelModal.tsx
```

`package.json` 显式声明 `three@0.185.1`、`@types/three@0.185.4`；本仓库 `.gitignore` 忽略 `package-lock.json`，本次未更改该既有策略。新检出环境需先 `npm install`。

继续工作前检查 `git status`。本地 `config/proxy.ts` 及其他血缘、工作流、监控文档是用户已有改动，未纳入本次提交，不得清理或覆盖。
