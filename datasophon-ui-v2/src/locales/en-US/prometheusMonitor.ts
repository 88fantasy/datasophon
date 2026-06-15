export default {
  'pages.prometheusMonitor.title': 'Prometheus Dashboard',

  // ── Stat panels (R1)
  'pages.prometheusMonitor.panel.uptime': 'Uptime [{interval}]',
  'pages.prometheusMonitor.panel.totalSeries': 'Total Series',
  'pages.prometheusMonitor.panel.memoryChunks': 'Memory Chunks',
  'pages.prometheusMonitor.panel.reloadFailures': 'Reload Failures [{interval}]',
  'pages.prometheusMonitor.panel.missedIterations': 'Missed Iterations [{interval}]',
  'pages.prometheusMonitor.panel.skippedScrapes': 'Skipped Scrapes [{interval}]',

  // ── Target health (R2)
  'pages.prometheusMonitor.panel.currentlyDown': 'Currently Down',
  'pages.prometheusMonitor.panel.upness': 'Upness (stacked)',

  // ── Scrape latency (R3)
  'pages.prometheusMonitor.panel.scrapeDuration': 'Scrape Duration',
  'pages.prometheusMonitor.panel.targetSync': 'Target Sync (ms)',

  // ── Scrape pool (R4)
  'pages.prometheusMonitor.panel.scrapeSyncTotal': 'Scrape Sync Total',
  'pages.prometheusMonitor.panel.rejectedScrapes': 'Rejected Scrapes',

  // ── TSDB Series (R5)
  'pages.prometheusMonitor.panel.seriesCount': 'Series Count',
  'pages.prometheusMonitor.panel.seriesCreatedRemoved': 'Series Created / Removed',
  'pages.prometheusMonitor.panel.appendedSamples': 'Appended Samples/s',

  // ── Storage & Go runtime (R6)
  'pages.prometheusMonitor.panel.storageChunks': 'Storage Memory Chunks',
  'pages.prometheusMonitor.panel.goMemory': 'Go Memory Usage',
  'pages.prometheusMonitor.panel.gcRate': 'GC Rate / 2m',

  // ── Rule evaluation & queries (R7)
  'pages.prometheusMonitor.panel.ruleEvalIterations': 'Rule Evaluator Iterations',
  'pages.prometheusMonitor.panel.ruleEvalDuration': 'Avg Rule Evaluation Duration',
  'pages.prometheusMonitor.panel.queryDuration': 'Query Engine Duration',

  // ── Errors & config & notifications (R8)
  'pages.prometheusMonitor.panel.failuresAndErrors': 'Failures and Errors',
  'pages.prometheusMonitor.panel.notificationsSent': 'Notifications Sent',
  'pages.prometheusMonitor.panel.configReloadMinutes': 'Mins Since Config Reload',

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
