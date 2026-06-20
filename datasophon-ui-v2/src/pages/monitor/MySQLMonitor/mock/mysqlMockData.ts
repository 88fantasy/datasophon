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

export const MOCK_INSTANCES = ['mysql-1:9104', 'mysql-2:9104'];
export const MOCK_JOBS = ['mysql'];

export const instantValues = {
  uptime: 259_200,
  currentQps: 1240,
  connectionsUsedPercent: 34,
  innodbBufferPool: 2_147_483_648,
  slowQueriesPerSecond: 0,
  abortedConnectionsPerSecond: 0,
};

export const mysqlSeriesData: Record<string, TimeSeriesPoint[]> = {
  M01: genSeries('Uptime', instantValues.uptime, 0, 1),
  M02: genSeries('Current QPS', instantValues.currentQps, 80, 2, {
    min: 1100,
    max: 1400,
  }),
  M03: genSeries('Connections Used %', instantValues.connectionsUsedPercent, 6, 3, {
    min: 28,
    max: 42,
  }),
  M04: genSeries('InnoDB Buffer Pool', instantValues.innodbBufferPool, 0, 4),
  M05: genSeries('Slow Queries /s', 0, 0, 5, {
    spikes: [{ near: 70, value: 0.3 }],
    max: 0.5,
  }),
  M06: genSeries('Aborted Connections /s', 0, 0, 6),
  M07: [
    ...genSeries('mysql-1:9104', 1240, 160, 10, { min: 1100, max: 1400 }),
    ...genSeries('mysql-2:9104', 1160, 120, 11, { min: 1050, max: 1350 }),
  ],
  M08: [
    ...genSeries('Inbound', 2 * 1024 * 1024, 256 * 1024, 20, {
      min: 1.6 * 1024 * 1024,
      max: 2.4 * 1024 * 1024,
    }),
    ...genSeries('Outbound', 8 * 1024 * 1024, 900 * 1024, 21, {
      min: 6.5 * 1024 * 1024,
      max: 9.5 * 1024 * 1024,
    }),
  ],
  M09: [
    ...genSeries('Connections', 38, 14, 30, { min: 30, max: 45 }),
    ...genSeries('Max Used', 120, 0, 31),
    ...genSeries('Max Connections', 151, 0, 32),
  ],
  M10: [
    ...genSeries('Threads Connected', 38, 14, 40, { min: 30, max: 45 }),
    ...genSeries('Threads Running', 4, 4, 41, { min: 2, max: 6 }),
  ],
  M11: genSeries('Slow Queries', 0, 0, 50, {
    spikes: [
      { near: 48, value: 0.2 },
      { near: 83, value: 0.5 },
    ],
    max: 0.5,
  }),
  M12: [
    ...genSeries('Aborted Connects', 0, 0, 60, {
      spikes: [{ near: 68, value: 1 }],
      max: 1,
    }),
    ...genSeries('Aborted Clients', 0, 0.02, 61, { max: 0.05 }),
  ],
  M13: [
    ...genSeries('Immediate', 200, 24, 70, { min: 180, max: 225 }),
    ...genSeries('Waited', 0, 0, 71, {
      spikes: [{ near: 78, value: 4 }],
      max: 4,
    }),
  ],
  M14: [
    ...genSeries('Buffer Pool Data', 1.5 * 1024 * 1024 * 1024, 24 * 1024 * 1024, 80),
    ...genSeries('Log Buffer', 16 * 1024 * 1024, 0, 81),
    ...genSeries('Key Buffer', 64 * 1024 * 1024, 0, 82),
    ...genSeries('Adaptive Hash', 80 * 1024 * 1024, 8 * 1024 * 1024, 83),
    ...genSeries('Query Cache', 0, 0, 84),
    ...genSeries('Additional Mem Pool', 0, 0, 85),
  ],
  M15: [
    ...genSeries('Tmp Tables', 10, 10, 90, { min: 5, max: 15 }),
    ...genSeries('Tmp Disk Tables', 0.2, 0.5, 91, {
      spikes: [{ near: 72, value: 1 }],
      max: 1,
    }),
    ...genSeries('Tmp Files', 0, 0.05, 92, { max: 0.1 }),
  ],
  M16: [
    ...genSeries('read_rnd_next', 5000, 700, 100, { min: 4200, max: 5700 }),
    ...genSeries('write', 800, 160, 101, { min: 650, max: 950 }),
    ...genSeries('update', 300, 90, 102, { min: 220, max: 390 }),
    ...genSeries('delete', 60, 30, 103, { max: 120 }),
    ...genSeries('read_key', 1800, 260, 104, { min: 1500, max: 2100 }),
  ],
  M17: [
    ...genSeries('select', 1200, 160, 110, { min: 1050, max: 1400 }),
    ...genSeries('insert', 200, 50, 111, { min: 150, max: 260 }),
    ...genSeries('update', 150, 40, 112, { min: 110, max: 210 }),
    ...genSeries('set_option', 90, 30, 113, { min: 60, max: 130 }),
    ...genSeries('commit', 80, 20, 114, { min: 60, max: 110 }),
  ],
};
