import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';

function prand(seed: number): number {
  const x = Math.sin(seed + 1) * 10000;
  return x - Math.floor(x);
}

const BASE_NOW = Date.now();
const STEP_MS = 30_000;
const POINTS = 120;
const MB = 1024 * 1024;
const GB = 1024 * MB;

function genSeries(
  series: string,
  base: number,
  variance: number,
  seed = 0,
  options?: {
    min?: number;
    max?: number;
    spikes?: Array<{ near: number; value: number }>;
  },
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, index) => {
    const spike = options?.spikes?.find((s) => Math.abs(s.near - index) < 2);
    const noise = (prand(index * 13 + seed) - 0.5) * variance;
    const rawValue = spike ? spike.value : base + noise;
    const value = Math.min(
      options?.max ?? Number.POSITIVE_INFINITY,
      Math.max(options?.min ?? 0, rawValue),
    );

    return {
      time: BASE_NOW - (POINTS - 1 - index) * STEP_MS,
      value,
      series,
    };
  });
}

export const MOCK_VOLUMES = ['analytics-fs', 'warehouse-fs'];

export const instantValues = {
  uptime: 432_000,
  dataSize: 5.4e11,
  files: 1_280_000,
  clientSessions: 3,
  cacheHitPercent: 94,
  stagingBlocks: 0,
};

export const juiceFSSeriesData: Record<string, TimeSeriesPoint[]> = {
  J07: [
    ...genSeries('client-1:9567', 420, 90, 10, { min: 200, max: 500 }),
    ...genSeries('client-2:9567', 330, 75, 11, { min: 200, max: 500 }),
    ...genSeries('client-3:9567', 260, 60, 12, { min: 200, max: 500 }),
  ],
  J08: [
    ...genSeries('Write', 40 * MB, 8 * MB, 20),
    ...genSeries('Read', 120 * MB, 20 * MB, 21),
  ],
  J09: [
    ...genSeries('client-1:/mnt/jfs', 950, 250, 30, { min: 800, max: 1500 }),
    ...genSeries('client-2:/mnt/jfs', 1150, 260, 31, { min: 800, max: 1500 }),
    ...genSeries('client-3:/mnt/jfs', 1300, 220, 32, { min: 800, max: 1500 }),
  ],
  J10: [
    ...genSeries('client-1:/mnt/jfs', 2800, 900, 40, {
      min: 2000,
      max: 5000,
    }),
    ...genSeries('client-2:/mnt/jfs', 3600, 1100, 41, {
      min: 2000,
      max: 5000,
    }),
  ],
  J11: [
    ...genSeries('client-1:9567', 22_000, 10_000, 50, {
      min: 15_000,
      max: 40_000,
    }),
    ...genSeries('client-2:9567', 31_000, 12_000, 51, {
      min: 15_000,
      max: 40_000,
    }),
  ],
  J12: [
    ...genSeries('GET', 80, 18, 60, { min: 60, max: 100 }),
    ...genSeries('PUT', 20, 8, 61, { min: 12, max: 30 }),
    ...genSeries('DELETE', 2, 1.5, 62, { min: 0, max: 5 }),
    ...genSeries('HEAD', 12, 4, 63, { min: 6, max: 18 }),
    ...genSeries('LIST', 4, 2, 64, { min: 1, max: 7 }),
  ],
  J13: [
    ...genSeries('Object Request Errors', 0, 0, 70, {
      spikes: [{ near: 76, value: 0.1 }],
      max: 0.1,
    }),
    ...genSeries('Transaction Restarts', 0.5, 0.25, 71, {
      min: 0.1,
      max: 0.9,
    }),
  ],
  J14: [
    ...genSeries('client-1:/mnt/jfs', 40 * GB, 2 * GB, 80),
    ...genSeries('client-2:/mnt/jfs', 38 * GB, 2 * GB, 81),
  ],
  J15: [
    ...genSeries('By Count', 94, 3, 90, { min: 88, max: 98 }),
    ...genSeries('By Bytes', 90, 4, 91, { min: 84, max: 96 }),
  ],
  J16: [
    ...genSeries('PUT', 30 * MB, 7 * MB, 100),
    ...genSeries('GET', 8 * MB, 3 * MB, 101),
  ],
  J17: [
    ...genSeries('CPU %', 45, 25, 110, { min: 30, max: 60 }),
    ...genSeries('Memory', 1.2 * GB, 120 * MB, 111),
  ],
};
