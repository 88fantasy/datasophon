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
}

const legacyRequestOptions = { baseURL: '/ddh/api' } as const;

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
