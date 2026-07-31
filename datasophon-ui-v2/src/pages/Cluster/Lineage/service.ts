import { request } from '@umijs/max';

export interface NodeMeta {
  id: number;
  clusterId: number;
  connector: string;
  catalogName: string;
  databaseName: string;
  tableName: string;
  canonicalName: string;
  dwLayer: string | null;
}

export interface GraphJob {
  jobId: number;
  edgeId: number;
  flowType: string;
}

export interface LogicalEdge {
  src: number;
  dst: number;
  jobs: GraphJob[];
}

export type LineageDirection = 'upstream' | 'downstream' | 'both';

export interface CollapsedNode {
  type: 'collapsed';
  nodeId: number;
  token: string;
  hiddenCount: number;
  direction: LineageDirection;
}

export interface GraphData {
  nodes: NodeMeta[];
  edges: LogicalEdge[];
  collapsed: CollapsedNode[];
  truncated: boolean;
}

export interface TablePage {
  list: NodeMeta[];
  total: number;
}

export interface LayerBlock {
  layer: string;
  nodeCount: number;
}

export interface LayerEdge {
  srcLayer: string;
  dstLayer: string;
  count: number;
}

export interface OverviewData {
  layers: LayerBlock[];
  edges: LayerEdge[];
}

export interface JobDetail {
  id: number;
  clusterId: number;
  jobName: string;
  engine: string;
  jobType: string;
  dwLayer: string | null;
  owner: string | null;
  externalUrl: string | null;
  state: string;
  updateTime: string;
}

export interface SnapshotFreshness {
  generation: number;
  targetGeneration: number;
  builtAt: string;
  ageSeconds: number;
  stale: boolean;
  lastRebuildError: string | null;
}

export type SourceFreshnessStatus = 'OK' | 'LAGGING' | 'NO_DATA' | 'UNKNOWN';

export interface SourceFreshness {
  lastEventReceivedAt: string | null;
  status: SourceFreshnessStatus;
}

export interface LineageQueryResponse<T> {
  data: T;
  snapshot: SnapshotFreshness;
  sourceFreshness: SourceFreshness;
}

export interface LeaseReadiness {
  owner: boolean;
  status: string;
  message: string;
}

export interface RebuildAccepted {
  generation: number;
}

interface RequestOptions {
  skipErrorHandler?: boolean;
}

function cleanParams<T extends object>(params: T) {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  );
}

export function getReadiness() {
  return request<LeaseReadiness>('/lineage/readiness', { method: 'GET' });
}

export interface ListTablesParams {
  clusterId: number;
  page?: number;
  size?: number;
  keyword?: string;
  layer?: string;
  connector?: string;
  database?: string;
}

export function listTables(params: ListTablesParams) {
  return request<LineageQueryResponse<TablePage>>('/lineage/tables', {
    method: 'GET',
    params: cleanParams(params),
  });
}

export interface GetGraphParams {
  clusterId: number;
  rootNodeId: number;
  depth?: number;
  direction?: LineageDirection;
  expand?: string;
}

export function getGraph(params: GetGraphParams, options?: RequestOptions) {
  return request<LineageQueryResponse<GraphData>>('/lineage/graph', {
    method: 'GET',
    params: cleanParams(params),
    ...options,
  });
}

export function getOverview(clusterId: number) {
  return request<LineageQueryResponse<OverviewData>>('/lineage/overview', {
    method: 'GET',
    params: { clusterId },
  });
}

export function getTable(clusterId: number, id: number) {
  return request<LineageQueryResponse<NodeMeta>>(`/lineage/table/${id}`, {
    method: 'GET',
    params: { clusterId },
  });
}

export function getJob(
  clusterId: number,
  id: number,
  options?: RequestOptions,
) {
  return request<JobDetail>(`/lineage/job/${id}`, {
    method: 'GET',
    params: { clusterId },
    ...options,
  });
}

export interface GetImpactParams {
  clusterId: number;
  rootNodeId: number;
  depth?: number;
}

export function getImpact(params: GetImpactParams, options?: RequestOptions) {
  return request<LineageQueryResponse<GraphData>>('/lineage/impact', {
    method: 'GET',
    params: cleanParams(params),
    ...options,
  });
}

export function rebuild(clusterId: number) {
  return request<RebuildAccepted>('/lineage/rebuild', {
    method: 'POST',
    params: { clusterId },
  });
}
