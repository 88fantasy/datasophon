/**
 * Prometheus 监控看板面板查询配置（P01–P24）。
 *
 * 所有 PromQL 来自 spec §5，含 $instance/$job/$interval 占位符，
 * 由 replaceVars() 在运行时替换为用户选择的变量值。
 */

// ─── 类型定义 ──────────────────────────────────────────────────────────────────

/** 单条 PromQL 的 instant 查询面板（P01–P06、P07） */
export interface InstantPanelDef {
  type: 'instant';
  promql: string;
}

/** 单条 PromQL 的 range 查询面板（大多数时序面板） */
export interface RangePanelDef {
  type: 'range';
  promql: string;
  /** 从哪个 label key 取 series 名（如 'instance'/'scrape_job'） */
  seriesKey?: string;
}

/** 多条 PromQL 合并为多系列的 range 查询面板（P12/P14/P19/P22） */
export interface MultiRangePanelDef {
  type: 'multi-range';
  queries: Array<{ label: string; promql: string }>;
}

export type PanelDef = InstantPanelDef | RangePanelDef | MultiRangePanelDef;

// ─── 面板配置（P01–P24）──────────────────────────────────────────────────────

/**
 * 完整看板查询配置映射。
 * key 与 index.tsx 中的面板 ID（P01/P08 等）一一对应。
 */
export const PANEL_QUERIES: Record<string, PanelDef> = {
  // ── R1：概览统计（instant） ───────────────────────────────────────────────

  P01: {
    type: 'instant',
    promql:
      'avg(avg_over_time(up{instance=~"$instance",job=~"$job"}[$interval]) * 100)',
  },
  P02: {
    type: 'instant',
    promql:
      'sum(prometheus_tsdb_head_series{job=~"$job",instance=~"$instance"})',
  },
  P03: {
    type: 'instant',
    promql:
      'sum(prometheus_tsdb_head_chunks{job=~"$job",instance=~"$instance"})',
  },
  P04: {
    type: 'instant',
    promql:
      'sum(sum_over_time(prometheus_tsdb_reloads_failures_total{job=~"$job",instance=~"$instance"}[$interval]))',
  },
  P05: {
    type: 'instant',
    promql:
      'sum(sum_over_time(prometheus_evaluator_iterations_missed_total{job=~"$job",instance=~"$instance"}[$interval]))',
  },
  P06: {
    type: 'instant',
    promql:
      'sum(sum_over_time(prometheus_target_scrapes_exceeded_sample_limit_total{job=~"$job",instance=~"$instance"}[$interval])) + ' +
      'sum(sum_over_time(prometheus_target_scrapes_sample_duplicate_timestamp_total{job=~"$job",instance=~"$instance"}[$interval])) + ' +
      'sum(sum_over_time(prometheus_target_scrapes_sample_out_of_bounds_total{job=~"$job",instance=~"$instance"}[$interval])) + ' +
      'sum(sum_over_time(prometheus_target_scrapes_sample_out_of_order_total{job=~"$job",instance=~"$instance"}[$interval]))',
  },

  // ── R2：目标健康 ─────────────────────────────────────────────────────────

  // P07 Currently Down 使用 vectorToTableRows，单独处理，此处也注册以保持结构完整
  P07: {
    type: 'instant',
    promql: 'up{instance=~"$instance",job=~"$job"} < 1',
  },
  P08: {
    type: 'range',
    promql: 'up{instance=~"$instance",job=~"$job"}',
    seriesKey: 'instance',
  },

  // ── R3：抓取延迟 ─────────────────────────────────────────────────────────

  P09: {
    type: 'range',
    promql: 'scrape_duration_seconds{instance=~"$instance"}',
    seriesKey: 'instance',
  },
  P10: {
    type: 'range',
    promql:
      'sum(rate(prometheus_target_sync_length_seconds_sum{job=~"$job",instance=~"$instance"}[2m])) by (scrape_job) * 1000',
    seriesKey: 'scrape_job',
  },

  // ── R4：抓取池 & 拒绝 ────────────────────────────────────────────────────

  P11: {
    type: 'range',
    promql:
      'sum(prometheus_target_scrape_pool_sync_total{job=~"$job",instance=~"$instance"}) by (scrape_job)',
    seriesKey: 'scrape_job',
  },
  P12: {
    type: 'multi-range',
    queries: [
      {
        label: 'sample_limit',
        promql:
          'sum(prometheus_target_scrapes_exceeded_sample_limit_total{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'duplicate_ts',
        promql:
          'sum(prometheus_target_scrapes_sample_duplicate_timestamp_total{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'out_of_bounds',
        promql:
          'sum(prometheus_target_scrapes_sample_out_of_bounds_total{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'out_of_order',
        promql:
          'sum(prometheus_target_scrapes_sample_out_of_order_total{job=~"$job",instance=~"$instance"})',
      },
    ],
  },

  // ── R5：TSDB Series ──────────────────────────────────────────────────────

  P13: {
    type: 'range',
    promql:
      'prometheus_tsdb_head_series{job=~"$job",instance=~"$instance"}',
    seriesKey: 'instance',
  },
  P14: {
    type: 'multi-range',
    queries: [
      {
        label: 'created',
        promql:
          'sum(increase(prometheus_tsdb_head_series_created_total{instance=~"$instance"}[5m]))',
      },
      {
        label: 'removed',
        promql:
          'sum(increase(prometheus_tsdb_head_series_removed_total{instance=~"$instance"}[5m]))',
      },
    ],
  },
  P15: {
    type: 'range',
    promql:
      'rate(prometheus_tsdb_head_samples_appended_total{job=~"$job",instance=~"$instance"}[1m])',
    seriesKey: 'instance',
  },

  // ── R6：存储 & Go 运行时 ──────────────────────────────────────────────────

  P16: {
    type: 'range',
    promql:
      'prometheus_tsdb_head_chunks{job=~"$job",instance=~"$instance"}',
    seriesKey: 'instance',
  },
  P17: {
    type: 'multi-range',
    queries: [
      {
        label: 'heap_alloc',
        promql:
          'sum(go_memstats_heap_alloc_bytes{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'heap_sys',
        promql:
          'sum(go_memstats_heap_sys_bytes{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'heap_inuse',
        promql:
          'sum(go_memstats_heap_inuse_bytes{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'heap_idle',
        promql:
          'sum(go_memstats_heap_idle_bytes{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'stack_inuse',
        promql:
          'sum(go_memstats_stack_inuse_bytes{job=~"$job",instance=~"$instance"})',
      },
      {
        label: 'sys',
        promql:
          'sum(go_memstats_sys_bytes{job=~"$job",instance=~"$instance"})',
      },
    ],
  },
  P18: {
    type: 'range',
    promql:
      'sum(rate(go_gc_duration_seconds_sum{instance=~"$instance",job=~"$job"}[2m])) by (instance)',
    seriesKey: 'instance',
  },

  // ── R7：规则评估 & 查询 ───────────────────────────────────────────────────

  P19: {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        promql:
          'sum(rate(prometheus_evaluator_iterations_total{job=~"$job",instance=~"$instance"}[5m]))',
      },
      {
        label: 'missed',
        promql:
          'sum(rate(prometheus_evaluator_iterations_missed_total{job=~"$job",instance=~"$instance"}[5m]))',
      },
      {
        label: 'skipped',
        promql:
          'sum(rate(prometheus_evaluator_iterations_skipped_total{job=~"$job",instance=~"$instance"}[5m]))',
      },
    ],
  },
  P20: {
    type: 'range',
    promql:
      '1000 * rate(prometheus_evaluator_duration_seconds_sum{job=~"$job",instance=~"$instance"}[5m]) / ' +
      'rate(prometheus_evaluator_duration_seconds_count{job=~"$job",instance=~"$instance"}[5m])',
    seriesKey: 'instance',
  },
  P21: {
    type: 'range',
    promql:
      'sum(prometheus_engine_query_duration_seconds_sum{job=~"$job",instance=~"$instance"}) by (slice)',
    seriesKey: 'slice',
  },

  // ── R8：错误 & 配置 & 通知 ────────────────────────────────────────────────

  P22: {
    type: 'multi-range',
    queries: [
      {
        label: 'conn_failed',
        promql:
          'sum(increase(net_conntrack_dialer_conn_failed_total{instance=~"$instance"}[5m])) > 0',
      },
      {
        label: 'rule_eval_failed',
        promql:
          'sum(increase(prometheus_rule_evaluation_failures_total{instance=~"$instance"}[5m])) > 0',
      },
      {
        label: 'scrape_sample_limit',
        promql:
          'sum(increase(prometheus_target_scrapes_exceeded_sample_limit_total{instance=~"$instance"}[5m])) > 0',
      },
      {
        label: 'tsdb_reload_failed',
        promql:
          'sum(increase(prometheus_tsdb_reloads_failures_total{instance=~"$instance"}[5m])) > 0',
      },
      {
        label: 'tsdb_compaction_failed',
        promql:
          'sum(increase(prometheus_tsdb_compactions_failed_total{instance=~"$instance"}[5m])) > 0',
      },
      {
        label: 'sample_out_of_order',
        promql:
          'sum(increase(prometheus_target_scrapes_sample_out_of_order_total{instance=~"$instance"}[5m])) > 0',
      },
    ],
  },
  P23: {
    type: 'range',
    promql:
      'rate(prometheus_notifications_sent_total{instance=~"$instance"}[5m])',
    seriesKey: 'instance',
  },
  P24: {
    type: 'range',
    promql:
      '(time() - prometheus_config_last_reload_success_timestamp_seconds{job=~"$job",instance=~"$instance"}) / 60',
    seriesKey: 'instance',
  },
};

/** range query 的时间范围（秒）映射 */
export const TIME_RANGE_SECONDS: Record<string, number> = {
  '5m': 300,
  '15m': 900,
  '1h': 3600,
  '6h': 21600,
  '24h': 86400,
  '7d': 604800,
};
