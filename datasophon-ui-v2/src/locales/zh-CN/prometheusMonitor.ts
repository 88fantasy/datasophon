export default {
  'pages.prometheusMonitor.title': 'Prometheus 监控看板',

  // ── 统计面板（R1）
  'pages.prometheusMonitor.panel.uptime': '可用率 [{interval}]',
  'pages.prometheusMonitor.panel.totalSeries': '总时序数',
  'pages.prometheusMonitor.panel.memoryChunks': '内存 Chunks',
  'pages.prometheusMonitor.panel.reloadFailures': '重载失败 [{interval}]',
  'pages.prometheusMonitor.panel.missedIterations': '遗漏迭代 [{interval}]',
  'pages.prometheusMonitor.panel.skippedScrapes': '跳过抓取 [{interval}]',

  // ── 目标健康（R2）
  'pages.prometheusMonitor.panel.currentlyDown': '当前宕机',
  'pages.prometheusMonitor.panel.upness': '可用性（堆叠）',

  // ── 抓取延迟（R3）
  'pages.prometheusMonitor.panel.scrapeDuration': '抓取耗时',
  'pages.prometheusMonitor.panel.targetSync': '目标同步 (ms)',

  // ── 抓取池（R4）
  'pages.prometheusMonitor.panel.scrapeSyncTotal': '抓取同步总量',
  'pages.prometheusMonitor.panel.rejectedScrapes': '被拒抓取',

  // ── TSDB Series（R5）
  'pages.prometheusMonitor.panel.seriesCount': '时序数量',
  'pages.prometheusMonitor.panel.seriesCreatedRemoved': '时序创建 / 删除',
  'pages.prometheusMonitor.panel.appendedSamples': '样本写入速率 (/s)',

  // ── 存储 & Go 运行时（R6）
  'pages.prometheusMonitor.panel.storageChunks': '存储 Chunks',
  'pages.prometheusMonitor.panel.goMemory': 'Go 内存用量',
  'pages.prometheusMonitor.panel.gcRate': 'GC 速率 / 2m',

  // ── 规则评估 & 查询（R7）
  'pages.prometheusMonitor.panel.ruleEvalIterations': '规则评估迭代',
  'pages.prometheusMonitor.panel.ruleEvalDuration': '平均规则评估耗时',
  'pages.prometheusMonitor.panel.queryDuration': '查询引擎耗时',

  // ── 错误 & 配置 & 通知（R8）
  'pages.prometheusMonitor.panel.failuresAndErrors': '失败与错误',
  'pages.prometheusMonitor.panel.notificationsSent': '已发通知',
  'pages.prometheusMonitor.panel.configReloadMinutes': '配置成功重载后分钟数',

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
