# 任务：实现 APISIX 监控看板原型（Phase 2）

## 背景

datasophon-ui 的服务实例"概览"页（`src/pages/ServiceManage/Instance/Overview/index.tsx`）
目前是一个 `<iframe>` 内嵌 Grafana 看板。本次任务是：

**将 APISIX 服务实例的 Overview 页，从 `<iframe>` 替换为原生 React + AntV G2 看板原型。**

本阶段（Phase 2）只做视觉原型：

- 使用静态 mock 数据（不接真实 Prometheus API）
- 完整实现布局、图表渲染、工具栏交互（时间范围切换、变量下拉、刷新按钮）

---

## 必读文档

**第一步必须完整阅读**：

```
uploads/apisix-dashboard-prototype-spec.md
```

该文档包含：

- 15 个面板的完整规格（PromQL、图表类型、单位、颜色、阈值）
- 7 行 24 列 Grid 布局（含 ASCII 示意图）
- AntV 图表类型映射字典
- TypeScript 接口定义
- 组件树结构
- 验收标准（10 条 checklist）

---

## 项目环境约束

- **框架**：React 19、TypeScript、Vite
- **UI 库**：`antd ^6.2.3`、`@ant-design/pro-components ^2.8.10`
- **图表库**：项目尚未安装 `@ant-design/charts`，**需要先执行**：
  ```bash
  cd datasophon-ui && pnpm add @ant-design/charts
  ```
  若 `@ant-design/charts` 与 antd v6 存在兼容性问题，改用 `@antv/g2` v5：
  ```bash
  pnpm add @antv/g2
  ```
  优先选前者（声明式更简洁）；遇到报错再切换后者，**不要两个都装**。
- **时间库**：`dayjs` 已安装，直接 import 使用，禁止安装 moment。
- **样式**：使用 Tailwind utility class，禁止新建 `.css` / `.module.css` 文件。
- **包管理器**：只用 `pnpm`，不用 npm / yarn。

---

## 文件放置规则

新文件全部放在：

```
datasophon-ui/src/pages/ServiceManage/Instance/Overview/
```

目录结构遵循 spec §8 组件树：

```
Overview/
  ├── index.tsx              ← 修改现有文件，替换 <iframe>
  ├── ApisixDashboard.tsx    ← 主看板容器
  ├── panels/
  │   ├── StatPanel.tsx
  │   ├── StatusStatPanel.tsx
  │   ├── TimeSeriesPanel.tsx
  │   └── AreaPanel.tsx
  ├── toolbar/
  │   └── DashboardToolbar.tsx
  ├── mock/
  │   └── apisixMockData.ts  ← 静态 mock 数据（本阶段用）
  └── utils/
      └── formatters.ts      ← formatBytes、colorByThreshold
```

**不要修改 `Overview/` 目录以外的任何文件。**

---

## Mock 数据要求

`apisixMockData.ts` 需要覆盖所有 15 个面板，包含：

- Instant query 返回值（P01-P05，单个数字）
- Range query 时序数据（P06-P15，最近 1 小时，步长 30s，共约 120 个点）
- 多系列面板（如 P07 按 status code 5 个系列、P04 按连接状态分组）

数据应体现真实感：

- RPS 约 50–300 req/s，带随机波动
- 延迟 p90 约 5–20ms，p99 约 20–80ms，偶有突刺
- 带宽 ingress > egress，量级在 KB/s 级别
- Etcd Reachable = 1（健康状态）、Nginx Metric Errors = 0

---

## 工具栏交互（必须实现）

1. **时间范围快速选择**：Last 5m / 15m / 1h（默认）/ 6h / 24h
   - 切换时，时序面板重新"刷新"（可只 re-render，mock 数据不变）
2. **实例下拉**：多选，mock 选项 `["10.0.0.1:9091", "10.0.0.2:9091"]`，默认全选
3. **服务下拉**：多选，mock 选项 `["order-service", "user-service", ".*"]`
4. **刷新间隔**：Off / 30s（默认）/ 1m，选 30s 后在页面上方显示倒计时"⟳ 18s"

---

## 验收标准（来自 spec §10）

完成后对照以下 checklist 自查：

- [ ] 全部 15 个面板有对应 React 组件 mock 展示
- [ ] 时序面板（P06-P15）使用 `@ant-design/charts` `<Line>` 或 `<Area>` 渲染
- [ ] 统计面板（P01-P05）使用 antd `<Statistic>` + 阈值染色
- [ ] 工具栏含时间范围快捷选择 + 刷新间隔 + 实例/服务下拉
- [ ] 布局与 spec §4 ASCII 图吻合（7 行 24 列 Grid，各行高度正确）
- [ ] 颜色遵循 spec §6.1 Token（primary 蓝、success 绿、warning 黄、error 红）
- [ ] TypeScript 无 `any` 类型（接口定义参考 spec §7）
- [ ] 1280px 宽度下不出现横向滚动条

完成后运行 `pnpm dev` 验证页面可正常渲染。
