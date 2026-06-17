/** 监控看板共享类型 */

export type TimeRange = '5m' | '15m' | '1h' | '6h' | '24h' | '7d';
export type RefreshInterval = 'off' | '30s' | '1m';

export interface TimeSeriesPoint {
  time: number;
  value: number;
  series: string;
}

export interface PrometheusTableRow {
  instance: string;
  job: string;
  value: number;
  key: string;
}
