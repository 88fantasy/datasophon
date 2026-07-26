import type { Dayjs } from 'dayjs';

export type ObservabilityTimeRange = [Dayjs, Dayjs];

export interface ObservabilityTabContext {
  timeRange: ObservabilityTimeRange;
  refreshKey: number;
}
