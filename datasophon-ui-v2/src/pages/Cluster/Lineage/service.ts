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
  jobName: string;
  lastRowCount: number | null;
  lastBytes: number | null;
  lastRunAt: string | null;
  runningAppId: string | null;
}

export interface JobMetrics {
  completeTasks: number;
  activeTasks: number;
  recordsWritten: number;
  bytesWritten: number;
  recordsWrittenRate: number | null;
  runningStages: number;
  sampledAt: string;
}

export type JobMetricsByAppId = Record<string, JobMetrics>;

export interface JobRatePoint {
  time: number;
  value: number;
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

export interface RebuildAccepted {
  generation: number;
}

interface RequestOptions {
  skipErrorHandler?: boolean;
}

/**
 * `V2ResponseBodyAdvice` 把 `com.datasophon.api.controller.v2` 包下所有控制器方法的成功返回值
 * 统一包一层 `ApiResponse{success,data,errorCode,errorMessage,showType}`——包括控制器本身已经
 * 返回 `LineageQueryResponse{data,snapshot,sourceFreshness}` 的端点，因此响应体是 `data.data`
 * 双层嵌套。这里统一在 service 层解包一次，调用方拿到的仍是 `LineageQueryResponse<T>` 本身，
 * 不需要感知这层信封。
 */
interface ApiEnvelope<T> {
  success: boolean;
  data: T;
}

function unwrap<T>(promise: Promise<ApiEnvelope<T>>): Promise<T> {
  return promise.then((envelope) => envelope.data);
}

function cleanParams<T extends object>(params: T) {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  );
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
  return unwrap(
    request<ApiEnvelope<LineageQueryResponse<TablePage>>>('/lineage/tables', {
      method: 'GET',
      params: cleanParams(params),
    }),
  );
}

export interface GetGraphParams {
  clusterId: number;
  rootNodeId: number;
  depth?: number;
  direction?: LineageDirection;
  expand?: string;
}

export function getGraph(params: GetGraphParams, options?: RequestOptions) {
  return unwrap(
    request<ApiEnvelope<LineageQueryResponse<GraphData>>>('/lineage/graph', {
      method: 'GET',
      params: cleanParams(params),
      ...options,
    }),
  );
}

export function getOverview(clusterId: number) {
  return unwrap(
    request<ApiEnvelope<LineageQueryResponse<OverviewData>>>(
      '/lineage/overview',
      { method: 'GET', params: { clusterId } },
    ),
  );
}

export function getTable(clusterId: number, id: number) {
  return unwrap(
    request<ApiEnvelope<LineageQueryResponse<NodeMeta>>>(
      `/lineage/table/${id}`,
      { method: 'GET', params: { clusterId } },
    ),
  );
}

export function getJob(
  clusterId: number,
  id: number,
  options?: RequestOptions,
) {
  return unwrap(
    request<ApiEnvelope<JobDetail>>(`/lineage/job/${id}`, {
      method: 'GET',
      params: { clusterId },
      ...options,
    }),
  );
}

export function getJobMetrics(clusterId: number, appIds: string[]) {
  if (appIds.length === 0) {
    return Promise.resolve({} as JobMetricsByAppId);
  }

  return unwrap(
    request<ApiEnvelope<JobMetricsByAppId>>('/lineage/job-metrics', {
      method: 'GET',
      params: { clusterId, appIds: appIds.join(',') },
      skipErrorHandler: true,
    }),
  );
}

interface PrometheusMatrix {
  resultType: 'matrix';
  result: Array<{
    metric: Record<string, string>;
    values: Array<[number, string]>;
  }>;
}

export function getJobRateHistory(clusterId: number, appId: string) {
  const end = Math.floor(Date.now() / 1000);
  const start = end - 3600;

  // 这里没有直接复用 monitor/_shared/dorisService.ts 的 queryDorisRange：它返回未解包的
  // ApiResponse 且不支持 skipErrorHandler，而这个查询失败时要静默显示"暂无速率数据"、
  // 不弹全局错误提示——强行复用会引入 lineage→monitor 的跨页耦合，还得在外面把这个行为
  // 重新包一层，不如就这样直接发请求。
  //
  // groupBy=app_id 与 T6 端点（LineageJobMetricsService）的口径保持一致：carbon receiver
  // 目前不写 resource attributes，所有 Spark 指标的 (service_instance_id, service_name)
  // 恰好同值，不传 groupBy 也能算对，但这依赖一个未来可能失效的偶然条件——一旦给 carbon
  // 链路加 resource processor，同一 app 的多条 series 就会被 SQL 默认分组拆开。显式声明
  // 分组维度，行为不再依赖这个偶然条件。
  return unwrap(
    request<ApiEnvelope<PrometheusMatrix>>(
      '/observability/otel/metrics/query_range',
      {
        method: 'GET',
        params: {
          clusterId,
          metric: 'spark_executor_recordsWritten',
          rateWindow: '1m',
          table: 'sum',
          filters: `app_id:${appId}`,
          groupBy: 'app_id',
          start,
          end,
          step: 60,
        },
        skipErrorHandler: true,
      },
    ),
  ).then((matrix) => {
    const totalsByTimestamp = new Map<number, number>();
    for (const series of matrix.result) {
      for (const [timestamp, rawValue] of series.values) {
        const value = Number(rawValue);
        if (Number.isFinite(value)) {
          totalsByTimestamp.set(
            timestamp,
            (totalsByTimestamp.get(timestamp) ?? 0) + value,
          );
        }
      }
    }

    return [...totalsByTimestamp.entries()]
      .sort(([left], [right]) => left - right)
      .map(([timestamp, value]) => ({ time: timestamp * 1000, value }));
  });
}

export interface GetImpactParams {
  clusterId: number;
  rootNodeId: number;
  depth?: number;
}

export function getImpact(params: GetImpactParams, options?: RequestOptions) {
  return unwrap(
    request<ApiEnvelope<LineageQueryResponse<GraphData>>>('/lineage/impact', {
      method: 'GET',
      params: cleanParams(params),
      ...options,
    }),
  );
}

export function rebuild(clusterId: number) {
  return unwrap(
    request<ApiEnvelope<RebuildAccepted>>('/lineage/rebuild', {
      method: 'POST',
      params: { clusterId },
    }),
  );
}
