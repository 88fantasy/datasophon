// Prometheus 监控看板（自监控 Prometheus 进程本身）已随后端 Prometheus 组件退役而移除；
// 本文件仅保留 toolbar.* 键 —— 被 _shared/DashboardToolbar.tsx 及多个看板专属工具栏
// （ZKDashboardToolbar / NginxDashboardToolbar / ValkeyDashboardToolbar 等）复用，不可删除。
export default {
  // ── Toolbar
  'pages.prometheusMonitor.toolbar.instance': '实例',
  'pages.prometheusMonitor.toolbar.refreshNow': '立即刷新',
  'pages.prometheusMonitor.toolbar.timeRange.last5m': '最近 5 分钟',
  'pages.prometheusMonitor.toolbar.timeRange.last15m': '最近 15 分钟',
  'pages.prometheusMonitor.toolbar.timeRange.last1h': '最近 1 小时',
  'pages.prometheusMonitor.toolbar.timeRange.last6h': '最近 6 小时',
  'pages.prometheusMonitor.toolbar.timeRange.last24h': '最近 24 小时',
  'pages.prometheusMonitor.toolbar.timeRange.last7d': '最近 7 天',
  'pages.prometheusMonitor.toolbar.refresh.off': '关闭',
  'pages.prometheusMonitor.toolbar.refresh.30s': '30 秒',
  'pages.prometheusMonitor.toolbar.refresh.1m': '1 分钟',
};
