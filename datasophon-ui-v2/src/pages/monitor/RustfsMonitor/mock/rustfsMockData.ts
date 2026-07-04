import type { TimeSeriesPoint } from '../../_shared/types';

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
  options?: { min?: number; max?: number },
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, index) => {
    const time = BASE_NOW - (POINTS - 1 - index) * STEP_MS;
    const noise = (prand(index * 17 + seed) - 0.5) * variance;
    const value = Math.min(
      options?.max ?? Number.POSITIVE_INFINITY,
      Math.max(options?.min ?? 0, base + noise),
    );

    return { time, value, series };
  });
}

export const MOCK_INSTANCES = ['rustfs-1:9000'];
export const MOCK_JOBS = ['rustfs'];

export const instantValues = {
  uptime: 432_000,
  buckets: 3,
  objects: 12_800,
  drivesOnline: 1,
  drivesOffline: 0,
};

export const rustfsSeriesData: Record<string, TimeSeriesPoint[]> = {
  R06: [
    ...genSeries('s3:GetObject', 12, 4, 10, { min: 5, max: 20 }),
    ...genSeries('s3:PutObject', 6, 3, 11, { min: 1, max: 12 }),
    ...genSeries('s3:HeadObject', 3, 1.5, 12, { min: 0, max: 6 }),
  ],
  R07: [
    ...genSeries('2xx', 18, 4, 20, { min: 10, max: 26 }),
    ...genSeries('4xx', 0.4, 0.3, 21, { max: 1 }),
  ],
  R08: [
    ...genSeries('Request', 2 * MB, 0.5 * MB, 30, { min: MB, max: 3 * MB }),
    ...genSeries('Response', 8 * MB, 2 * MB, 31, { min: 4 * MB, max: 12 * MB }),
  ],
  R09: [
    ...genSeries('p50', 0.008, 0.002, 40, { min: 0.004, max: 0.012 }),
    ...genSeries('p99', 0.045, 0.012, 41, { min: 0.03, max: 0.07 }),
  ],
  R10: [...genSeries('Failures', 0.02, 0.02, 50, { max: 0.1 })],
  R11: [
    ...genSeries('I/O', 0, 0.01, 60, { max: 0.05 }),
    ...genSeries('Timeout', 0, 0.005, 61, { max: 0.02 }),
    ...genSeries('Availability', 0, 0, 62),
  ],
  R12: [...genSeries('Used %', 42, 3, 70, { min: 35, max: 50 })],
  R13: [...genSeries('CPU %', 18, 6, 80, { min: 8, max: 35 })],
  R14: [
    ...genSeries('Memory', 1.2 * GB, 100 * MB, 90, {
      min: GB,
      max: 1.5 * GB,
    }),
  ],
  R15: [
    ...genSeries('Used', 220 * GB, 5 * GB, 100, { min: 200 * GB, max: 240 * GB }),
    ...genSeries('Total', 500 * GB, 0, 101),
  ],
  R16: [
    ...genSeries('Reads/s', 40, 15, 110, { min: 10, max: 80 }),
    ...genSeries('Writes/s', 25, 10, 111, { min: 5, max: 50 }),
  ],
  R17: [
    ...genSeries('Open', 180, 20, 120, { min: 140, max: 220 }),
    ...genSeries('Limit', 65536, 0, 121),
  ],
  R18: [...genSeries('Active Workers', 2, 1, 130, { min: 0, max: 5 })],
};
