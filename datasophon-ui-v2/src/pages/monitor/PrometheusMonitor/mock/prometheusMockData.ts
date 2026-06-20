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

function prand(seed: number): number {
  const x = Math.sin(seed + 1) * 10000;
  return x - Math.floor(x);
}

const BASE_NOW = Date.now();
const STEP_MS = 30_000;
const POINTS = 120;

function genSeries(
  series: string,
  base: number,
  variance: number,
  seed = 0,
  options?: {
    trend?: number;
    min?: number;
    max?: number;
    spikes?: Array<{ near: number; mul: number }>;
    dips?: Array<{ near: number; value: number }>;
  },
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, index) => {
    const time = BASE_NOW - (POINTS - 1 - index) * STEP_MS;
    const noise = (prand(index * 11 + seed) - 0.5) * variance;
    const spike = options?.spikes?.find((s) => Math.abs(s.near - index) < 3);
    const dip = options?.dips?.find((d) => Math.abs(d.near - index) < 2);
    const trended = base + noise + (options?.trend ?? 0) * index;
    const multiplied = spike ? trended * spike.mul : trended;
    const bounded = Math.min(
      options?.max ?? Number.POSITIVE_INFINITY,
      Math.max(options?.min ?? 0, multiplied),
    );

    return {
      time,
      value: dip ? dip.value : bounded,
      series,
    };
  });
}

export const MOCK_INSTANCES = ['prometheus-0:9090', 'prometheus-1:9090'];
export const MOCK_JOBS = ['prometheus', 'datasophon-prometheus'];

export const instantValues = {
  uptime: 99.8,
  totalSeries: 125_000,
  memoryChunks: 45_600,
  reloadFailures: 0,
  missedIterations: 0,
  skippedScrapes: 2,
};

export const currentlyDownRows: PrometheusTableRow[] = [];

const upnessData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 1, 0, 1, {
    min: 0,
    max: 1,
    dips: [{ near: 48, value: 0 }],
  }),
  ...genSeries('prometheus-1:9090', 1, 0, 2, {
    min: 0,
    max: 1,
    dips: [{ near: 76, value: 0 }],
  }),
];

const scrapeDurationData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 0.004, 0.004, 10, { max: 0.01 }),
  ...genSeries('prometheus-1:9090', 0.006, 0.004, 11, { max: 0.01 }),
];

const targetSyncData: TimeSeriesPoint[] = [
  ...genSeries('prometheus', 12, 8, 20, { spikes: [{ near: 86, mul: 2.5 }] }),
  ...genSeries('node', 8, 5, 21),
  ...genSeries('kubernetes', 18, 9, 22),
];

const scrapeSyncTotalData: TimeSeriesPoint[] = [
  ...genSeries('prometheus', 1000, 2, 30, { trend: 1.2 }),
  ...genSeries('node', 1100, 2, 31, { trend: 1.5 }),
  ...genSeries('kubernetes', 800, 2, 32, { trend: 0.9 }),
];

const rejectedScrapesData: TimeSeriesPoint[] = [
  ...genSeries('sample_limit', 0.2, 0.3, 40, { spikes: [{ near: 64, mul: 8 }] }),
  ...genSeries('duplicate_ts', 0.05, 0.1, 41),
  ...genSeries('out_of_bounds', 0.02, 0.05, 42),
  ...genSeries('out_of_order', 0.08, 0.1, 43),
];

const seriesCountData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 62_000, 1200, 50, { trend: 8 }),
  ...genSeries('prometheus-1:9090', 63_000, 1100, 51, { trend: 7 }),
];

const seriesCreatedRemovedData: TimeSeriesPoint[] = [
  ...genSeries('created', 180, 70, 60),
  ...genSeries('removed', 120, 45, 61),
];

const appendedSamplesData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 260, 120, 70, { min: 100, max: 500 }),
  ...genSeries('prometheus-1:9090', 310, 140, 71, { min: 100, max: 500 }),
];

const storageMemoryChunksData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 22_000, 900, 80, { trend: 3 }),
  ...genSeries('prometheus-1:9090', 23_600, 850, 81, { trend: 2 }),
];

const goMemoryData: TimeSeriesPoint[] = [
  ...genSeries('heap_alloc', 50 * 1024 * 1024, 6 * 1024 * 1024, 90, {
    trend: 35_000,
  }),
  ...genSeries('heap_sys', 200 * 1024 * 1024, 12 * 1024 * 1024, 91),
  ...genSeries('heap_inuse', 80 * 1024 * 1024, 8 * 1024 * 1024, 92),
  ...genSeries('heap_idle', 110 * 1024 * 1024, 9 * 1024 * 1024, 93),
  ...genSeries('stack_inuse', 12 * 1024 * 1024, 2 * 1024 * 1024, 94),
  ...genSeries('sys', 300 * 1024 * 1024, 20 * 1024 * 1024, 95, {
    trend: 45_000,
  }),
];

const gcRateData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 0.0015, 0.001, 100, { max: 0.006 }),
  ...genSeries('prometheus-1:9090', 0.002, 0.001, 101, { max: 0.006 }),
];

const ruleEvaluatorData: TimeSeriesPoint[] = [
  ...genSeries('total', 20, 4, 110),
  ...genSeries('missed', 0.03, 0.08, 111, { max: 0.3 }),
  ...genSeries('skipped', 0.05, 0.08, 112, { max: 0.3 }),
];

const avgRuleEvalDurationData: TimeSeriesPoint[] = [
  ...genSeries('duration', 12, 10, 120, {
    max: 50,
    spikes: [{ near: 75, mul: 3 }],
  }),
];

const engineQueryDurationData: TimeSeriesPoint[] = [
  ...genSeries('inner_eval', 0.012, 0.008, 130),
  ...genSeries('prepare_time', 0.004, 0.003, 131),
  ...genSeries('queue_time', 0.002, 0.002, 132),
  ...genSeries('result_sort', 0.006, 0.004, 133),
];

const failuresAndErrorsData: TimeSeriesPoint[] = [
  ...genSeries('conn_failed', 0, 0, 140),
  ...genSeries('rule_eval_failed', 0, 0, 141, {
    spikes: [{ near: 82, mul: 1 }],
  }).map((point, index) => ({ ...point, value: index === 82 ? 1 : 0 })),
  ...genSeries('scrape_sample_limit', 0, 0, 142),
  ...genSeries('tsdb_reload_failed', 0, 0, 143),
  ...genSeries('tsdb_compaction_failed', 0, 0, 144),
  ...genSeries('sample_out_of_order', 0, 0, 145),
];

const notificationsSentData: TimeSeriesPoint[] = [
  ...genSeries('notifications', 0.4, 0.35, 150, { max: 2 }),
];

const minutesSinceConfigReloadData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-0:9090', 18, 1.5, 160, { trend: 0.5 }),
  ...genSeries('prometheus-1:9090', 24, 1.5, 161, { trend: 0.5 }),
];

export const prometheusSeriesData: Record<string, TimeSeriesPoint[]> = {
  P08: upnessData,
  P09: scrapeDurationData,
  P10: targetSyncData,
  P11: scrapeSyncTotalData,
  P12: rejectedScrapesData,
  P13: seriesCountData,
  P14: seriesCreatedRemovedData,
  P15: appendedSamplesData,
  P16: storageMemoryChunksData,
  P17: goMemoryData,
  P18: gcRateData,
  P19: ruleEvaluatorData,
  P20: avgRuleEvalDurationData,
  P21: engineQueryDurationData,
  P22: failuresAndErrorsData,
  P23: notificationsSentData,
  P24: minutesSinceConfigReloadData,
};
