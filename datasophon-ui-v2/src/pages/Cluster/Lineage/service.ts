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

/**
 * 一个 Flink JobID 是固定 32 位小写十六进制串；Spark 的 app_id 从不长这样
 * （`local-<epoch>` / `application_<epoch>_<seq>`）。跟后端 `LineageJobMetricsService`
 * 用同一条规则区分两种引擎，appId 参数不需要额外带引擎标记。
 */
const FLINK_JOB_ID = /^[0-9a-f]{32}$/;

/**
 * Flink 有两套互不相交的指标命名（见 T12/T16 记录）：原生 OTLP push（FLIP-385）用点号命名，
 * Prometheus scrape 兜底路径（如 Flink 1.20 没有 FLIP-385）用下划线命名。一个 job_id 只会落在
 * 其中一套里，并行查两套再按时间戳求和是安全的——不会重复计数。
 *
 * 两套命名落的 Doris 表也不一样：Flink 自带的 Prometheus Reporter 把所有 Counter 类型指标
 * （含 numRecordsOut）在 `/metrics` 输出里都误标成 `# TYPE ... gauge`（Flink 自身的已知行为，
 * 不是 Collector 的 bug），OTel Collector 尊重这个 TYPE 标注，下划线命名的指标因此落进
 * otel_metrics_gauge，而不是 otel_metrics_sum；原生 OTLP push 走标准 Sum 语义，落 sum 表不受
 * 影响。查错表不会报错，只会静默返回空结果——这是 T16 后续排查（2026-08-07）用真实数据核实到的。
 */
const FLINK_RECORDS_OUT_METRICS = [
  // Native OTLP 的 Doris Writer 没有 numRecordsOut，Committer 的值恒为 0。Writer 的
  // numRecordsIn 是每个采样间隔内实际交给 Doris connector 的行数（delta Sum）；按 60 秒桶求和
  // 再除以 60，才是实际行/秒，不能按 monotonic counter 的相邻值差分。
  {
    metric: 'flink.taskmanager.job.task.operator.numRecordsIn',
    table: 'sum',
    operatorRegex: '.*Writer.*',
    valueAggregation: 'sum',
    scale: 1 / 60,
  },
  {
    metric: 'flink_taskmanager_job_task_operator_numRecordsOut',
    table: 'gauge',
    operatorRegex: '.*(Writer|Committer).*',
    rateWindow: '1m',
  },
];

function queryRateMatrix(
  clusterId: number,
  params: Record<string, string | number>,
) {
  // 这里没有直接复用 monitor/_shared/dorisService.ts 的 queryDorisRange：它返回未解包的
  // ApiResponse 且不支持 skipErrorHandler，而这个查询失败时要静默显示"暂无速率数据"、
  // 不弹全局错误提示——强行复用会引入 lineage→monitor 的跨页耦合，还得在外面把这个行为
  // 重新包一层，不如就这样直接发请求。
  return unwrap(
    request<ApiEnvelope<PrometheusMatrix>>(
      '/observability/otel/metrics/query_range',
      {
        method: 'GET',
        params: { clusterId, ...params },
        skipErrorHandler: true,
      },
    ),
  ).catch(() => ({ resultType: 'matrix' as const, result: [] }));
}

function sumMatricesByTimestamp(matrices: PrometheusMatrix[]): JobRatePoint[] {
  const totalsByTimestamp = new Map<number, number>();
  for (const matrix of matrices) {
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
  }
  return [...totalsByTimestamp.entries()]
    .sort(([left], [right]) => left - right)
    .map(([timestamp, value]) => ({ time: timestamp * 1000, value }));
}

export function getJobRateHistory(clusterId: number, appId: string) {
  const end = Math.floor(Date.now() / 1000);
  const start = end - 3600;
  const rangeParams = { start, end, step: 60 };

  if (FLINK_JOB_ID.test(appId)) {
    return Promise.all(
      FLINK_RECORDS_OUT_METRICS.map(
        ({
          metric,
          table,
          operatorRegex,
          rateWindow,
          valueAggregation,
          scale,
        }) =>
          queryRateMatrix(clusterId, {
            ...rangeParams,
            table,
            metric,
            filters: `job_id:${appId}`,
            filtersRegex: `operator_name:${operatorRegex}`,
            groupBy: 'job_id',
            ...(rateWindow ? { rateWindow } : {}),
            ...(valueAggregation ? { valueAggregation } : {}),
            ...(scale ? { scale } : {}),
          }),
      ),
    ).then(sumMatricesByTimestamp);
  }

  // groupBy=app_id 与 T6 端点（LineageJobMetricsService）的口径保持一致：carbon receiver
  // 目前不写 resource attributes，所有 Spark 指标的 (service_instance_id, service_name)
  // 恰好同值，不传 groupBy 也能算对，但这依赖一个未来可能失效的偶然条件——一旦给 carbon
  // 链路加 resource processor，同一 app 的多条 series 就会被 SQL 默认分组拆开。显式声明
  // 分组维度，行为不再依赖这个偶然条件。
  return queryRateMatrix(clusterId, {
    ...rangeParams,
    metric: 'spark_executor_recordsWritten',
    rateWindow: '1m',
    table: 'sum',
    filters: `app_id:${appId}`,
    groupBy: 'app_id',
  }).then((matrix) => sumMatricesByTimestamp([matrix]));
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
