import type { TimeSeriesPoint } from '../../PrometheusMonitor/mock/prometheusMockData';

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

function genCounter(
  series: string,
  perSecond: number,
  seed: number,
): TimeSeriesPoint[] {
  let value = 25_000 + seed * 100;
  return Array.from({ length: POINTS }, (_, index) => {
    value += perSecond * (STEP_MS / 1000) + prand(index + seed) * 80;
    return {
      time: BASE_NOW - (POINTS - 1 - index) * STEP_MS,
      value,
      series,
    };
  });
}

export const MOCK_INSTANCES = ['zk-1:7000', 'zk-2:7000', 'zk-3:7000'];
export const MOCK_JOBS = ['zookeeper'];

export const instantValues = {
  quorumSize: 3,
  leaderUptime: 7_254_000,
  jvmThreads: 85,
  deadlockedThreads: 0,
  aliveConnections: 42,
  openFileDescriptors: 356,
};

export const zkSeriesData: Record<string, TimeSeriesPoint[]> = {
  Z07: [
    ...genSeries('zk-1:7000', 1, 2, 10, {
      max: 15,
      spikes: [{ near: 78, value: 15 }],
    }),
    ...genSeries('zk-2:7000', 0.8, 1.6, 11, { max: 3 }),
    ...genSeries('zk-3:7000', 1.2, 1.8, 12, { max: 3 }),
  ],
  Z08: [
    ...genSeries('max', 32, 25, 20, {
      max: 110,
      spikes: [{ near: 80, value: 100 }],
    }),
    ...genSeries('avg', 3.5, 2, 21, { min: 2, max: 5 }),
    ...genSeries('min', 1, 0.8, 22, { max: 2 }),
  ],
  Z09: [
    ...genSeries('global', 60, 10, 30, { min: 55, max: 65 }),
    ...genSeries('local', 45, 8, 31, { min: 40, max: 50 }),
  ],
  Z10: [
    ...genSeries('znode_count', 12_000, 120, 40, { trend: 1.5 }),
    ...genSeries('ephemerals', 200, 40, 41),
  ],
  Z11: genSeries('data_size', 2.5 * 1024 * 1024, 80 * 1024, 50, {
    trend: 1024,
  }),
  Z12: [
    ...genSeries('zk-1:7000', 500, 60, 60),
    ...genSeries('zk-2:7000', 480, 55, 61),
    ...genSeries('zk-3:7000', 520, 65, 62),
  ],
  Z13: [
    ...genCounter('received', 1000, 70),
    ...genCounter('sent', 980, 71),
  ],
  Z14: [
    ...genSeries('zk-1:7000', 14, 3, 80, { min: 10, max: 18 }),
    ...genSeries('zk-2:7000', 14, 3, 81, { min: 10, max: 18 }),
    ...genSeries('zk-3:7000', 14, 3, 82, { min: 10, max: 18 }),
  ],
  Z15: [
    ...genSeries('conn_rejected', 0, 0, 90, {
      spikes: [{ near: 76, value: 1 }],
      max: 1,
    }),
    ...genSeries('conn_drop', 0, 0, 91),
    ...genSeries('unrecoverable', 0, 0, 92),
    ...genSeries('digest_mismatch', 0, 0, 93),
  ],
  Z16: genSeries('election_time', 0, 0, 100, {
    spikes: [{ near: 72, value: 180 }],
  }),
  Z17: [
    ...genSeries('learners', 2, 0.2, 110, { min: 2, max: 2 }),
    ...genSeries('synced_observers', 0, 0, 111),
  ],
  Z18: [
    ...genCounter('commits', 35, 120),
    ...genCounter('snapshots', 0.01, 121),
    ...genCounter('proposals', 36, 122),
  ],
  Z19: [
    ...genSeries('zk-1:7000', 3, 4, 130, {
      max: 20,
      spikes: [{ near: 82, value: 20 }],
    }),
    ...genSeries('zk-2:7000', 2.5, 3, 131, { max: 8 }),
    ...genSeries('zk-3:7000', 3.2, 3, 132, { max: 8 }),
  ],
  Z20: [
    ...genSeries('zk-1:7000', 360, 180, 140, { min: 200, max: 500 }),
    ...genSeries('zk-2:7000', 330, 160, 141, { min: 200, max: 500 }),
    ...genSeries('zk-3:7000', 380, 170, 142, { min: 200, max: 500 }),
  ],
  Z21: [
    ...genSeries('G1 Old Gen', 120 * 1024 * 1024, 12 * 1024 * 1024, 150),
    ...genSeries('Metaspace', 60 * 1024 * 1024, 5 * 1024 * 1024, 151),
    ...genSeries('G1 Eden Space', 24 * 1024 * 1024, 20 * 1024 * 1024, 152),
  ],
  Z22: [
    ...genSeries('G1 Young Generation', 0.01, 0.006, 160, { max: 0.03 }),
    ...genSeries('G1 Old Generation', 0, 0.001, 161, { max: 0.002 }),
  ],
  Z23: [
    ...genSeries('zk-1:7000', 0.5, 0.25, 170, { max: 1 }),
    ...genSeries('zk-2:7000', 0.45, 0.25, 171, { max: 1 }),
    ...genSeries('zk-3:7000', 0.55, 0.25, 172, { max: 1 }),
  ],
};
