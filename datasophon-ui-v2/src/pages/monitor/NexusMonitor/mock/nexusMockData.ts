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
  options?: {
    trend?: number;
    min?: number;
    max?: number;
    spikes?: Array<{ near: number; value?: number; mul?: number }>;
    sawtooth?: number;
  },
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, index) => {
    const time = BASE_NOW - (POINTS - 1 - index) * STEP_MS;
    const noise = (prand(index * 17 + seed) - 0.5) * variance;
    const tooth = options?.sawtooth ? (index % options.sawtooth) / options.sawtooth : 0;
    const trended = base + noise + (options?.trend ?? 0) * index + tooth * variance;
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

export const MOCK_INSTANCES = ['nexus-1:8081', 'nexus-2:8081'];
export const MOCK_JOBS = ['nexus'];

export const instantValues = {
  uptime: 604_800_000,
  heapRatio: 42,
  fdRatio: 18,
  readonlyEnabled: 0,
  jvmThreads: 186,
  deadlockThreads: 0,
};

export const nexusSeriesData: Record<string, TimeSeriesPoint[]> = {
  N01: genSeries('Uptime', instantValues.uptime, 0),
  N02: genSeries('Heap Ratio', instantValues.heapRatio, 0),
  N03: genSeries('FileDescriptor Ratio', instantValues.fdRatio, 0),
  N04: genSeries('Readonly Enabled', instantValues.readonlyEnabled, 0),
  N05: genSeries('JVM Threads', instantValues.jvmThreads, 0),
  N06: genSeries('Deadlock Threads', instantValues.deadlockThreads, 0),
  N07: [
    ...genSeries('1xx', 0.01, 0.02, 10, { max: 0.05 }),
    ...genSeries('2xx', 12, 2.2, 11, { min: 10, max: 14 }),
    ...genSeries('3xx', 1, 0.3, 12, { min: 0.6, max: 1.4 }),
    ...genSeries('4xx', 0.2, 0.2, 13, { max: 0.6 }),
    ...genSeries('5xx', 0, 0.02, 14, {
      max: 1.2,
      spikes: [{ near: 76, value: 1.1 }],
    }),
  ],
  N08: [
    ...genSeries('SearchComponent_read', 0, 0, 20, {
      max: 1,
      spikes: [{ near: 74, value: 1 }],
    }),
    ...genSeries('RepositoryComponent_read', 0, 0, 21),
  ],
  N09: [
    ...genSeries('p50', 8, 2, 30, { min: 5, max: 12 }),
    ...genSeries('p99', 45, 12, 31, { min: 30, max: 70 }),
  ],
  N10: [
    ...genSeries('Repository', 30, 8, 40, { min: 20, max: 45 }),
    ...genSeries('Search', 120, 45, 41, {
      min: 75,
      max: 190,
      spikes: [{ near: 82, value: 210 }],
    }),
    ...genSeries('Browse', 25, 8, 42, { min: 15, max: 40 }),
    ...genSeries('Security', 5, 2, 43, { min: 2, max: 9 }),
  ],
  N11: [
    ...genSeries('get', 12, 4, 50, { min: 8, max: 18 }),
    ...genSeries('create', 35, 10, 51, { min: 22, max: 55 }),
    ...genSeries('delete', 20, 7, 52, { min: 12, max: 32 }),
    ...genSeries('copy', 50, 16, 53, { min: 30, max: 85 }),
  ],
  N12: [
    ...genSeries('Max', 4 * GB, 0, 60),
    ...genSeries('Used', 1.5 * GB, 300 * MB, 61, {
      min: 1.5 * GB,
      max: 1.8 * GB,
      sawtooth: 28,
    }),
    ...genSeries('Committed', 2 * GB, 0, 62),
  ],
  N13: [
    ...genSeries('Eden', 40 * MB, 360 * MB, 70, {
      min: 0,
      max: 400 * MB,
      sawtooth: 24,
    }),
    ...genSeries('Old Gen', 1.2 * GB, 80 * MB, 71, {
      min: 1.1 * GB,
      max: 1.3 * GB,
    }),
    ...genSeries('Survivor', 30 * MB, 6 * MB, 72, {
      min: 24 * MB,
      max: 36 * MB,
    }),
    ...genSeries('Metaspace', 180 * MB, 10 * MB, 73, {
      min: 170 * MB,
      max: 190 * MB,
    }),
    ...genSeries('Code Cache', 90 * MB, 6 * MB, 74, {
      min: 84 * MB,
      max: 96 * MB,
    }),
  ],
  N14: [
    ...genSeries('MarkSweep', 0, 0.002, 80, { max: 0.004 }),
    ...genSeries('Scavenge', 0.05, 0.02, 81, { min: 0.02, max: 0.08 }),
  ],
  N15: [
    ...genSeries('MarkSweep', 0, 1, 90, {
      max: 90,
      spikes: [{ near: 70, value: 80 }],
    }),
    ...genSeries('Scavenge', 15, 6, 91, { min: 8, max: 24 }),
  ],
  N16: [
    ...genSeries('Runnable', 40, 8, 100, { min: 32, max: 48 }),
    ...genSeries('Blocked', 0, 0.4, 101, { max: 2 }),
    ...genSeries('Waiting', 25, 6, 102, { min: 18, max: 32 }),
    ...genSeries('Timed Waiting', 120, 20, 103, { min: 100, max: 140 }),
  ],
  N17: [
    ...genSeries('Queued Jobs', 1, 4, 110, { min: 0, max: 3 }),
    ...genSeries('Pool Size', 200, 4, 111, { min: 196, max: 204 }),
  ],
  N18: [
    ...genSeries('Non-Heap', 280 * MB, 18 * MB, 120, {
      min: 260 * MB,
      max: 300 * MB,
    }),
    ...genSeries('Direct Buffers', 64 * MB, 6 * MB, 121, {
      min: 58 * MB,
      max: 70 * MB,
    }),
    ...genSeries('Mapped Buffers', 0, 0, 122),
  ],
};
