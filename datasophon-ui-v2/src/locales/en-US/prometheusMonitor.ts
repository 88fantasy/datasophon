// The Prometheus dashboard (self-monitoring the Prometheus process itself) was removed
// along with the retired backend Prometheus component. This file now only keeps the
// toolbar.* keys, which are reused by _shared/DashboardToolbar.tsx and several
// service-specific toolbars (ZKDashboardToolbar / NginxDashboardToolbar /
// ValkeyDashboardToolbar, etc.) — do not delete.
export default {
  // ── Toolbar
  'pages.prometheusMonitor.toolbar.instance': 'Instance',
  'pages.prometheusMonitor.toolbar.refreshNow': 'Refresh Now',
  'pages.prometheusMonitor.toolbar.timeRange.last5m': 'Last 5m',
  'pages.prometheusMonitor.toolbar.timeRange.last15m': 'Last 15m',
  'pages.prometheusMonitor.toolbar.timeRange.last1h': 'Last 1h',
  'pages.prometheusMonitor.toolbar.timeRange.last6h': 'Last 6h',
  'pages.prometheusMonitor.toolbar.timeRange.last24h': 'Last 24h',
  'pages.prometheusMonitor.toolbar.timeRange.last7d': 'Last 7d',
  'pages.prometheusMonitor.toolbar.refresh.off': 'Off',
  'pages.prometheusMonitor.toolbar.refresh.30s': '30s',
  'pages.prometheusMonitor.toolbar.refresh.1m': '1m',
};
