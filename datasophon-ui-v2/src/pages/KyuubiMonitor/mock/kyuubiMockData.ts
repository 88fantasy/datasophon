import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';

function prand(seed: number): number {
  const x = Math.sin(seed + 1) * 10000;
  return x - Math.floor(x);
}

const BASE_NOW = Date.now();
const STEP_MS = 30_000;
const POINTS = 120;
const GB = 1024 ** 3;
const MB = 1024 ** 2;

function genSeries(
  series: string,
  base: number,
  variance: number,
  seed = 0,
  options?: {
    trend?: number;
    min?: number;
    max?: number;
    saw?: number;
    spikes?: Array<{ near: number; value?: number; mul?: number }>;
  },
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, index) => {
    const time = BASE_NOW - (POINTS - 1 - index) * STEP_MS;
    const noise = (prand(index * 13 + seed) - 0.5) * variance;
    const saw = options?.saw ? (index % options.saw) / options.saw : 0;
    const trended = base + noise + (options?.trend ?? 0) * index + saw * variance;
    const spike = options?.spikes?.find((s) => Math.abs(s.near - index) < 2);
    const spiked =
      spike?.value !== undefined ? spike.value : trended * (spike?.mul ?? 1);
    const value = Math.min(
      options?.max ?? Number.POSITIVE_INFINITY,
      Math.max(options?.min ?? 0, spike ? spiked : trended),
    );

    return { time, value, series };
  });
}

export const MOCK_INSTANCES = ['kyuubi-1:10019', 'kyuubi-2:10019'];
export const MOCK_CONN_TYPES = [
  'connection_total_INTERACTIVE',
  'connection_total_BATCH',
];
export const MOCK_OP_TYPES = [
  'ExecuteStatement',
  'LaunchEngine',
  'GetSchemas',
  'GetTables',
  'GetColumns',
  'GetFunctions',
  'GetCatalogs',
  'GetTypeInfo',
];

export const instantValues = {
  KY01: 2,
  KY02: 172_800,
  KY03: 45,
  KY04: 8,
  KY05: 64,
  KY06: 0,
};

export const kyuubiSeriesData: Record<string, TimeSeriesPoint[]> = {
  KY07: [
    ...genSeries('kyuubi-1:10019', 10, 10, 10, { min: 5, max: 15 }),
    ...genSeries('kyuubi-2:10019', 8, 8, 11, { min: 5, max: 15 }),
  ],
  KY08: [
    ...genSeries('kyuubi-1:10019', 52, 46, 20, { min: 30, max: 80 }),
    ...genSeries('kyuubi-2:10019', 45, 38, 21, { min: 30, max: 80 }),
  ],
  KY09: [
    ...genSeries('Pending', 1.2, 2.5, 30, { min: 0, max: 3 }),
    ...genSeries('Running', 4, 3, 31, { min: 2, max: 6 }),
  ],
  KY10: [
    ...genSeries('Launching', 0.6, 1.5, 40, { min: 0, max: 2 }),
    ...genSeries('Startup Permit Limit', 10, 0, 41, { min: 10, max: 10 }),
  ],
  KY11: genSeries('kyuubi-1:10019', 0, 0, 50, {
    max: 1,
    spikes: [{ near: 72, value: 1 }],
  }),
  KY12: [
    ...genSeries('Operation Error', 0, 0, 60, {
      max: 2,
      spikes: [{ near: 80, value: 2 }],
    }),
    ...genSeries('Operation Failed', 0, 0, 61, {
      max: 1,
      spikes: [{ near: 82, value: 1 }],
    }),
    ...genSeries('Engine Open Failed', 0, 0, 62),
  ],
  KY13: [
    ...genSeries('kyuubi-1:10019', 380_000, 160_000, 70, {
      min: 220_000,
      max: 620_000,
    }),
    ...genSeries('kyuubi-2:10019', 300_000, 120_000, 71, {
      min: 180_000,
      max: 520_000,
    }),
  ],
  KY14: [
    ...genSeries('kyuubi-1:10019', 900, 900, 80, {
      min: 200,
      max: 2000,
    }),
    ...genSeries('kyuubi-2:10019', 760, 700, 81, {
      min: 200,
      max: 1800,
    }),
  ],
  KY15: [
    ...genSeries('Used', 3 * GB, 0.3 * GB, 90, {
      min: 2.6 * GB,
      max: 3.4 * GB,
    }),
    ...genSeries('Usage Ratio', 0.45, 0.06, 91, {
      min: 0.4,
      max: 0.5,
    }),
  ],
  KY16: [
    ...genSeries('Old Gen', 2 * GB, 0.2 * GB, 100, {
      min: 1.8 * GB,
      max: 2.2 * GB,
    }),
    ...genSeries('Eden', 620 * MB, 480 * MB, 101, {
      min: 120 * MB,
      max: 1.1 * GB,
      saw: 18,
    }),
    ...genSeries('Survivor', 140 * MB, 40 * MB, 102, {
      min: 80 * MB,
      max: 200 * MB,
    }),
    ...genSeries('Metaspace', 200 * MB, 20 * MB, 103, {
      min: 180 * MB,
      max: 220 * MB,
    }),
    ...genSeries('Code Cache', 96 * MB, 12 * MB, 104, {
      min: 84 * MB,
      max: 110 * MB,
    }),
  ],
};
