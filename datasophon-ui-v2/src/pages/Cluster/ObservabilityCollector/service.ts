import { request } from '@umijs/max';

export interface CollectorConfigField {
  name: string;
  label?: string;
  description?: string;
  required?: boolean;
  type?: string;
  value?: unknown;
  defaultValue?: unknown;
  hidden?: boolean;
  configurableInWizard?: boolean;
  minValue?: number;
  maxValue?: number;
  selectValue?: string[];
}

export interface CollectorSelfMetrics {
  queueSize: number;
  queueCapacity: number;
  sentTotal: number;
  sendFailedTotal: number;
  refusedTotal: number;
  processorDroppedTotal: number;
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

export function getCollectorConfig(clusterId: number) {
  return request<ApiResult<CollectorConfigField[]>>(
    '/observability/otelcol/config',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId },
    },
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

export function pushCollectorConfig(
  clusterId: number,
  hostname: string,
  data: Record<string, string>,
) {
  return request<ApiResult<void>>('/observability/otelcol/push', {
    ...legacyRequestOptions,
    method: 'POST',
    params: { clusterId, hostname },
    data,
  });
}

export function listTraces(params: TraceQueryParams) {
  return request<ApiResult<TraceRow[]>>('/observability/otelcol/traces', {
    ...legacyRequestOptions,
    method: 'GET',
    params: cleanParams(params),
  });
}

export function getTraceDetail(clusterId: number, traceId: string) {
  return request<ApiResult<SpanNode[]>>('/observability/otelcol/traces/detail', {
    ...legacyRequestOptions,
    method: 'GET',
    params: { clusterId, traceId },
  });
}

export function listTraceServices(
  clusterId: number,
  start: number,
  end: number,
) {
  return request<ApiResult<string[]>>('/observability/otelcol/traces/services', {
    ...legacyRequestOptions,
    method: 'GET',
    params: { clusterId, start, end },
  });
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
