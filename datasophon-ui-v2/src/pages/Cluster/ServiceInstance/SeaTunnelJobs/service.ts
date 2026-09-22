import { request } from '@umijs/max';
import type {
  SeaTunnelJobInfo,
  SeaTunnelJobState,
  SeaTunnelOverview,
  SeaTunnelPendingJobs,
  SeaTunnelWorkers,
} from './types';

function seatunnelPath(clusterId: number, instanceId: number) {
  return `/cluster/${clusterId}/service/${instanceId}/seatunnel`;
}

export function getSeaTunnelOverview(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<SeaTunnelOverview>>(
    `${seatunnelPath(clusterId, instanceId)}/overview`,
    { method: 'GET', skipErrorHandler: true },
  );
}

export function getSeaTunnelWorkers(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<SeaTunnelWorkers>>(
    `${seatunnelPath(clusterId, instanceId)}/workers`,
    { method: 'GET', skipErrorHandler: true },
  );
}

export function getSeaTunnelPendingJobs(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<SeaTunnelPendingJobs>>(
    `${seatunnelPath(clusterId, instanceId)}/pending`,
    { method: 'GET', skipErrorHandler: true },
  );
}

export function getSeaTunnelJobs(
  clusterId: number,
  instanceId: number,
  state: SeaTunnelJobState,
) {
  return request<DATASOPHON.ApiResponse<SeaTunnelJobInfo[]>>(
    `${seatunnelPath(clusterId, instanceId)}/jobs`,
    { method: 'GET', params: { state }, skipErrorHandler: true },
  );
}

export function getSeaTunnelJobInfo(
  clusterId: number,
  instanceId: number,
  jobId: string,
) {
  return request<DATASOPHON.ApiResponse<SeaTunnelJobInfo>>(
    `${seatunnelPath(clusterId, instanceId)}/jobs/${encodeURIComponent(jobId)}`,
    { method: 'GET', skipErrorHandler: true },
  );
}
