import type { TimeSeriesPoint } from '../../_shared/types';

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
    spikes?: Array<{ near: number; value?: number; mul?: number }>;
  },
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, index) => {
    const time = BASE_NOW - (POINTS - 1 - index) * STEP_MS;
    const noise = (prand(index * 11 + seed) - 0.5) * variance;
    const trended = base + noise + (options?.trend ?? 0) * index;
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

export const MOCK_APPLICATIONS = ['datart'];
export const MOCK_INSTANCES = ['datart-1:8080', 'datart-2:8080'];
export const MOCK_HEAP_POOLS = ['G1 Old Gen', 'G1 Eden Space', 'G1 Survivor Space'];
export const MOCK_HIKARICP_POOLS = ['HikariPool-1'];

export const instantValues = {
  uptime: 259_200,
  heapUsedPercent: 58,
  nonHeapUsedPercent: 64,
  cpuUsage: 22,
  hikaricpActive: 4,
  errorLogsPerSecond: 0,
};

export const datartSeriesData: Record<string, TimeSeriesPoint[]> = {
  D01: genSeries('Uptime', instantValues.uptime, 0, 1),
  D02: genSeries('Heap Used %', instantValues.heapUsedPercent, 4, 2, {
    min: 50,
    max: 65,
  }),
  D03: genSeries('NonHeap Used %', instantValues.nonHeapUsedPercent, 3, 3, {
    min: 58,
    max: 70,
  }),
  D04: genSeries('CPU Usage', instantValues.cpuUsage, 8, 4, {
    min: 12,
    max: 38,
  }),
  D05: genSeries('HikariCP Active', instantValues.hikaricpActive, 3, 5, {
    min: 2,
    max: 6,
  }),
  D06: genSeries('Error Logs /s', instantValues.errorLogsPerSecond, 0, 6),
  D07: [
    ...genSeries('/datart/api/v1/viz', 8, 6, 10, { min: 1, max: 20 }),
    ...genSeries('/datart/view', 4, 4, 11, { min: 1, max: 14 }),
    ...genSeries('/datart/api/v1/source', 2, 2, 12, { min: 1, max: 7 }),
  ],
  D08: [
    ...genSeries('/datart/api/v1/viz', 0.04, 0.04, 20, {
      min: 0.02,
      max: 0.08,
    }),
    ...genSeries('/datart/view', 0.07, 0.04, 21, {
      min: 0.03,
      max: 0.5,
      spikes: [{ near: 82, value: 0.5 }],
    }),
    ...genSeries('/datart/api/v1/source', 0.035, 0.025, 22, {
      min: 0.02,
      max: 0.07,
    }),
  ],
  D09: [
    ...genSeries('System CPU', 0.35, 0.12, 30, { min: 0.2, max: 0.5 }),
    ...genSeries('Process CPU', 0.22, 0.08, 31, { min: 0.1, max: 0.38 }),
    ...genSeries('Load 1m', 1.8, 0.35, 32, { min: 1.2, max: 2.4 }),
  ],
  D10: [
    ...genSeries('Used', 1.2 * 1024 * 1024 * 1024, 80 * 1024 * 1024, 40),
    ...genSeries('Committed', 1.5 * 1024 * 1024 * 1024, 20 * 1024 * 1024, 41),
    ...genSeries('Max', 2 * 1024 * 1024 * 1024, 0, 42),
  ],
  D11: [
    ...genSeries('end of minor GC', 0.5, 0.2, 50, { min: 0.25, max: 0.8 }),
    ...genSeries('end of major GC', 0.01, 0.02, 51, { max: 0.05 }),
  ],
  D12: genSeries('GC Pause', 0.01, 0.01, 60, { max: 0.03 }),
  D13: [
    ...genSeries('Daemon', 40, 3, 70, { min: 36, max: 44 }),
    ...genSeries('Live', 60, 5, 71, { min: 55, max: 66 }),
    ...genSeries('Peak', 72, 2, 72, { min: 70, max: 76 }),
  ],
  D14: [
    ...genSeries('Active', 4, 4, 80, { min: 2, max: 6 }),
    ...genSeries('Idle', 6, 4, 81, { min: 4, max: 8 }),
    ...genSeries('Pending', 0, 0, 82, {
      max: 1,
      spikes: [{ near: 86, value: 1 }],
    }),
  ],
  D15: [
    ...genSeries('Acquire Time', 0.002, 0.001, 90, {
      min: 0.001,
      max: 0.006,
    }),
    ...genSeries('Usage Time', 0.015, 0.008, 91, {
      min: 0.008,
      max: 0.04,
    }),
  ],
  D16: [
    ...genSeries('Current Threads', 25, 4, 100, { min: 20, max: 30 }),
    ...genSeries('Busy Threads', 3, 3, 101, { min: 1, max: 6 }),
    ...genSeries('Active Sessions', 18, 5, 102, { min: 12, max: 24 }),
  ],
  D17: [
    ...genSeries('Sent', 2 * 1024 * 1024, 512 * 1024, 110, {
      min: 1.2 * 1024 * 1024,
      max: 3 * 1024 * 1024,
    }),
    ...genSeries('Received', 0.5 * 1024 * 1024, 160 * 1024, 111, {
      min: 0.25 * 1024 * 1024,
      max: 0.8 * 1024 * 1024,
    }),
  ],
  D18: [
    ...genSeries('info', 12, 4, 120, { min: 8, max: 16 }),
    ...genSeries('warn', 0.5, 0.5, 121, { min: 0, max: 1.5 }),
    ...genSeries('error', 0, 0, 122, {
      spikes: [{ near: 74, value: 0.8 }],
      max: 1,
    }),
    ...genSeries('debug', 0, 0.05, 123, { max: 0.1 }),
  ],
};
