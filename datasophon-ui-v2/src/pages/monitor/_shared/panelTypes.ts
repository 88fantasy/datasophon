/** Prometheus 看板面板查询类型定义 + 时间范围常量 */

/** 单条 PromQL 的 instant 查询面板 */
export interface InstantPanelDef {
  type: 'instant';
  promql: string;
}

/** 单条 PromQL 的 range 查询面板 */
export interface RangePanelDef {
  type: 'range';
  promql: string;
  /** 从哪个 label key 取 series 名（如 'instance'/'scrape_job'） */
  seriesKey?: string;
}

/** 多条 PromQL 合并为多系列的 range 查询面板 */
export interface MultiRangePanelDef {
  type: 'multi-range';
  queries: Array<{ label: string; promql: string }>;
}

export type PanelDef = InstantPanelDef | RangePanelDef | MultiRangePanelDef;

/** range query 的时间范围（秒）映射 */
export const TIME_RANGE_SECONDS: Record<string, number> = {
  '5m': 300,
  '15m': 900,
  '1h': 3600,
  '6h': 21600,
  '24h': 86400,
  '7d': 604800,
};
