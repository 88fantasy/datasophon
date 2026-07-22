import { request } from '@umijs/max';

export interface CollectorSelfMetrics {
  queueSize: number;
  queueCapacity: number;
  sentTotal: number;
  sendFailedTotal: number;
  receiverFailedTotal: number;
  refusedTotal: number;
  processorDroppedTotal: number;
  processUptime: number;
}

export interface CollectorNodeMetrics {
  hostname: string;
  healthy: boolean;
  error?: string;
  metrics?: CollectorSelfMetrics;
}

interface ApiResult<T> {
  code: number;
  msg?: string;
  data: T;
  total?: number;
}

const legacyRequestOptions = { baseURL: '/ddh/api' } as const;

export interface TraceRow {
  timestamp: string;
  serviceName: string;
  spanName: string;
  traceId: string;
  spanCount: number;
  duration: number;
  status: 'OK' | 'ERROR' | string;
}

export interface SpanNode {
  spanId: string;
  parentSpanId: string;
  spanName: string;
  spanKind: string;
  serviceName: string;
  timestamp: string;
  endTime: string;
  duration: number;
  statusCode: string;
  statusMessage: string;
  spanAttributes: Record<string, unknown>;
  resourceAttributes: Record<string, unknown>;
  events: unknown[];
}

export interface LogRow {
  timestamp: string;
  severityText: string;
  serviceName: string;
  body: string;
  traceId: string;
  spanId: string;
  logAttributes: Record<string, unknown>;
  resourceAttributes: Record<string, unknown>;
}

export interface TopologyNode {
  serviceName: string;
  spanCount: number;
  errorCount: number;
  avgDurationNs: number;
  p99DurationNs: number;
  maxDurationNs: number;
  external?: boolean;
  dbSystem?: string;
  /** 该外部依赖是否真的落到 db.system(而非 rpc.system/http/other 兜底),DB 徽标据此判定。 */
  isDatabase?: boolean;
  /** 后端按 ip:port 反查出的真实服务类型(如 "doris"/"datasophon-worker"),查不到时为空,前端回退按 dbSystem 展示。 */
  serviceType?: string;
}

export interface TopologyEdge {
  caller: string;
  callee: string;
  callCount: number;
  errorCount: number;
}

export interface TopologyGraph {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
}

export interface ServiceSummaryStats {
  spanCount: number;
  errorCount: number;
  avgDurationNs: number;
  p99DurationNs: number;
  maxDurationNs: number;
}

export interface ServiceSummaryPoint {
  time: string;
  spanCount: number;
  errorCount: number;
  avgDurationNs: number;
}

export interface ServiceSummary {
  current: ServiceSummaryStats;
  previous: ServiceSummaryStats;
  series: ServiceSummaryPoint[];
}

export interface TraceQueryParams {
  clusterId: number;
  start: number;
  end: number;
  serviceName?: string;
  status?: string;
  spanName?: string;
  traceId?: string;
  page?: number;
  pageSize?: number;
}

export interface LogQueryParams {
  clusterId: number;
  start: number;
  end: number;
  serviceName?: string;
  severities?: string[];
  bodyKeyword?: string;
  traceId?: string;
  page?: number;
  pageSize?: number;
}

function cleanParams<T extends object>(params: T) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => {
      if (Array.isArray(value)) return value.length > 0;
      return value !== undefined && value !== null && value !== '';
    }),
  );
}

export function getCollectorMonitor(clusterId: number) {
  return request<ApiResult<CollectorNodeMetrics[]>>(
    '/observability/otelcol/monitor',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId },
    },
  );
}

export function listTraces(params: TraceQueryParams) {
  return request<ApiResult<TraceRow[]>>('/observability/otelcol/traces', {
    ...legacyRequestOptions,
    method: 'GET',
    params: cleanParams(params),
  });
}

export function getTraceDetail(clusterId: number, traceId: string) {
  return request<ApiResult<SpanNode[]>>(
    '/observability/otelcol/traces/detail',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId, traceId },
    },
  );
}

export function listTraceServices(
  clusterId: number,
  start: number,
  end: number,
) {
  return request<ApiResult<string[]>>(
    '/observability/otelcol/traces/services',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId, start, end },
    },
  );
}

export function getTraceTopology(
  clusterId: number,
  start: number,
  end: number,
) {
  return request<ApiResult<TopologyGraph>>(
    '/observability/otelcol/traces/topology',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId, start, end },
    },
  );
}

export function getServiceSummary(
  clusterId: number,
  start: number,
  end: number,
  serviceName: string,
) {
  return request<ApiResult<ServiceSummary>>(
    '/observability/otelcol/traces/service-summary',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId, start, end, serviceName },
    },
  );
}

export function listLogs(params: LogQueryParams) {
  return request<ApiResult<LogRow[]>>('/observability/otelcol/logs', {
    ...legacyRequestOptions,
    method: 'GET',
    params: cleanParams({
      ...params,
      severities: params.severities?.join(','),
    }),
  });
}
