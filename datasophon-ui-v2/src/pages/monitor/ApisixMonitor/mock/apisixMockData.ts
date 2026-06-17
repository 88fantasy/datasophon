export interface TimeSeriesPoint {
  time: number;
  value: number;
  series: string;
}

// 确定性伪随机，避免每次渲染数据跳动
function prand(seed: number): number {
  const x = Math.sin(seed + 1) * 10000;
  return x - Math.floor(x);
}

const BASE_NOW = Date.now();
const STEP_MS = 30_000;
const POINTS = 120;

function genSeries(
  name: string,
  base: number,
  variance: number,
  seed = 0,
  spikes: Array<{ near: number; mul: number }> = [],
): TimeSeriesPoint[] {
  return Array.from({ length: POINTS }, (_, i) => {
    const time = BASE_NOW - (POINTS - 1 - i) * STEP_MS;
    const noise = (prand(i * 7 + seed) - 0.5) * variance;
    const spike = spikes.find((s) => Math.abs(s.near - i) < 4);
    const mul = spike ? spike.mul : 1;
    return { time, value: Math.max(0, (base + noise) * mul), series: name };
  });
}

// ── P01-P05 Instant values ──────────────────────────────────────────
export const instantValues = {
  totalRequests: 1_482_356,
  acceptedConnections: 318,
  handledConnections: 315,
  etcdReachable: 1,
  nginxMetricErrors: 0,
};

// ── P06 Total RPS ──────────────────────────────────────────────────
export const totalRpsData: TimeSeriesPoint[] = genSeries('RPS', 160, 60, 1, [
  { near: 40, mul: 1.8 },
  { near: 90, mul: 0.4 },
]);

// ── P07 RPS by Status Code ─────────────────────────────────────────
export const rpsByCodeData: TimeSeriesPoint[] = [
  ...genSeries('200', 130, 40, 10, [{ near: 40, mul: 1.8 }]),
  ...genSeries('301', 8, 4, 20),
  ...genSeries('400', 12, 6, 30, [{ near: 60, mul: 2.5 }]),
  ...genSeries('404', 6, 3, 40),
  ...genSeries('500', 4, 3, 50, [{ near: 90, mul: 4 }]),
];

// ── P08 Request Latency (p90/p95/p99) ─────────────────────────────
export const requestLatencyData: TimeSeriesPoint[] = [
  ...genSeries('p90', 12, 4, 60),
  ...genSeries('p95', 22, 6, 61, [{ near: 75, mul: 2.2 }]),
  ...genSeries('p99', 45, 12, 62, [{ near: 75, mul: 2.8 }]),
];

// ── P09 APISIX Latency ─────────────────────────────────────────────
export const apisixLatencyData: TimeSeriesPoint[] = [
  ...genSeries('p90', 2, 0.8, 70),
  ...genSeries('p95', 4, 1.2, 71),
  ...genSeries('p99', 9, 3, 72),
];

// ── P10 Upstream Latency ───────────────────────────────────────────
export const upstreamLatencyData: TimeSeriesPoint[] = [
  ...genSeries('p90', 9, 3, 80),
  ...genSeries('p95', 17, 5, 81, [{ near: 75, mul: 2.0 }]),
  ...genSeries('p99', 36, 10, 82, [{ near: 75, mul: 2.6 }]),
];

// ── P11 Total Bandwidth (bytes/s) ──────────────────────────────────
export const bandwidthData: TimeSeriesPoint[] = [
  ...genSeries('ingress', 180_000, 60_000, 90, [{ near: 40, mul: 1.7 }]),
  ...genSeries('egress', 95_000, 30_000, 91),
];

// ── P12 RPS per Service ────────────────────────────────────────────
export const rpsPerServiceData: TimeSeriesPoint[] = [
  ...genSeries('order-service', 80, 25, 100),
  ...genSeries('user-service', 55, 18, 101),
  ...genSeries('payment-service', 30, 12, 102),
];

// ── P13 Nginx Connection State ─────────────────────────────────────
export const connectionStateData: TimeSeriesPoint[] = [
  ...genSeries('active', 290, 40, 110),
  ...genSeries('reading', 12, 5, 111),
  ...genSeries('writing', 28, 10, 112),
  ...genSeries('waiting', 250, 35, 113),
];

// ── P14 Shared Dict Free Space (%) ────────────────────────────────
export const sharedDictData: TimeSeriesPoint[] = [
  ...genSeries('prometheus-metrics', 78, 3, 120),
  ...genSeries('plugin-limit-req', 91, 2, 121),
  ...genSeries('plugin-limit-conn', 85, 2, 122),
  ...genSeries('balancer-ewma', 95, 1, 123),
];

// ── P15 Etcd Modify Indexes ────────────────────────────────────────
export const etcdIndexData: TimeSeriesPoint[] = [
  ...genSeries('routes', 1024 + POINTS * 0.5, 2, 130),
  ...genSeries('services', 512 + POINTS * 0.3, 1, 131),
  ...genSeries('upstreams', 256 + POINTS * 0.2, 1, 132),
  ...genSeries('consumers', 128 + POINTS * 0.1, 0.5, 133),
  ...genSeries('ssls', 64, 0.3, 134),
].map((pt, idx) => ({
  ...pt,
  // etcd indexes 是单调递增的
  value: pt.value + Math.floor(idx / 5) * 0.8,
}));
