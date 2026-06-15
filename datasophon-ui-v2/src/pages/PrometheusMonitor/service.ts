import { request } from '@umijs/max';
import type { PrometheusMatrix, PrometheusVector } from './utils/promql';

/** 后端 ApiResponse 信封（v2 约定） */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  errorCode?: number;
  errorMessage?: string;
}

/** instant query 参数 */
export interface InstantQueryParams {
  query: string;
  /** Unix 秒（可选，缺省取当前时间） */
  time?: number;
  /** 集群 ID（后端当前忽略，保留参数兼容） */
  clusterId?: number;
}

/** range query 参数 */
export interface RangeQueryParams {
  query: string;
  start: number;
  end: number;
  step: number;
  /** 集群 ID（后端当前忽略，保留参数兼容） */
  clusterId?: number;
}

/**
 * Prometheus instant query。
 *
 * 返回 `res.data` 即 `{resultType:'vector', result:[...]}`。
 * 路径相对于 baseURL（/ddh/api/v2），实际请求为 /ddh/api/v2/prometheus/query。
 */
export function queryInstant(params: InstantQueryParams) {
  return request<ApiResponse<PrometheusVector>>('/prometheus/query', {
    method: 'GET',
    params,
  });
}

/**
 * Prometheus range query。
 *
 * 返回 `res.data` 即 `{resultType:'matrix', result:[...]}`。
 */
export function queryRange(params: RangeQueryParams) {
  return request<ApiResponse<PrometheusMatrix>>('/prometheus/query_range', {
    method: 'GET',
    params,
  });
}
