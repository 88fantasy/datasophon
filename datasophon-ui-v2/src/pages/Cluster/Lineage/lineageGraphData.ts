import type {
  CollapsedNode,
  GraphData,
  GraphJob,
  JobMetricsByAppId,
  LogicalEdge,
} from './service';
import { formatRunningJobLabel } from './lineageFormatters';

export interface G6NodeData {
  canonicalName: string;
  /** 下列表元信息只有真实的表节点才有；折叠占位节点没有对应的库表实体 */
  connector?: string;
  catalogName?: string;
  databaseName?: string;
  tableName?: string;
  dwLayer: string | null;
  isRoot: boolean;
  isCollapsedPlaceholder: boolean;
  hiddenCount?: number;
  expandToken?: string;
  impactHighlighted: boolean;
  // G6 的 NodeData.data 要求 Record<string, unknown> 索引签名
  [key: string]: unknown;
}

/** 作业写入某一张目标表的统计（一个多输出作业对应多条）。 */
export interface JobOutputStat {
  dstNodeId: number;
  dstName: string;
  lastRowCount: number | null;
  lastBytes: number | null;
  lastRunAt: string | null;
}

export interface G6JobNodeData extends GraphJob {
  isJobNode: true;
  isRoot: false;
  isCollapsedPlaceholder: false;
  impactHighlighted: false;
  /** 按目标表拆分的统计；单输出作业下与节点本身的 lastRowCount 等字段一致，长度为 1。 */
  outputs: JobOutputStat[];
  [key: string]: unknown;
}

export interface G6EdgeData {
  jobs: GraphJob[];
  isCollapsedLink: boolean;
  [key: string]: unknown;
}

export interface G6Node {
  id: string;
  data: G6NodeData | G6JobNodeData;
  [key: string]: unknown;
}

export interface G6Edge {
  id: string;
  source: string;
  target: string;
  data: G6EdgeData;
  [key: string]: unknown;
}

export interface G6GraphData {
  nodes: G6Node[];
  edges: G6Edge[];
}

/**
 * 折叠占位节点(`+N`)按查询方向决定挂在触发节点的哪一侧——upstream 时占位代表"更靠上游的隐藏节点"，
 * 画在触发节点前面；downstream/both 统一画在后面，语义上不完全精确（both 混合了双向隐藏邻居），
 * 但作为"点击展开更多"的可视化提示已经足够，不需要拆分成两个占位节点。
 */
export function toG6Data(
  graph: GraphData,
  rootNodeId: number,
  impactHighlightIds?: Set<number>,
): G6GraphData {
  const nodes: G6Node[] = graph.nodes.map((node) => ({
    id: String(node.id),
    data: {
      canonicalName: node.canonicalName,
      connector: node.connector,
      catalogName: node.catalogName,
      databaseName: node.databaseName,
      tableName: node.tableName,
      dwLayer: node.dwLayer,
      isRoot: node.id === rootNodeId,
      isCollapsedPlaceholder: false,
      impactHighlighted: impactHighlightIds?.has(node.id) ?? false,
    },
  }));
  const nodeNameById = new Map<number, string>(
    graph.nodes.map((node) => [node.id, node.canonicalName]),
  );
  // 按 (jobId, dstNodeId) 去重：同一个 job 若因多个源表汇入同一目标表而出现在多条边上，
  // 后端为该 dst 算出的统计是同一份，这里不能把它重复计入 outputs。
  const jobOutputs = new Map<number, Map<number, JobOutputStat>>();
  graph.edges.forEach((edge) => {
    edge.jobs.forEach((job) => {
      const outputsByDst = jobOutputs.get(job.jobId) ?? new Map<number, JobOutputStat>();
      outputsByDst.set(edge.dst, {
        dstNodeId: edge.dst,
        dstName: nodeNameById.get(edge.dst) ?? String(edge.dst),
        lastRowCount: job.lastRowCount,
        lastBytes: job.lastBytes,
        lastRunAt: job.lastRunAt,
      });
      jobOutputs.set(job.jobId, outputsByDst);
    });
  });
  const jobNodes = new Map<number, G6Node>();
  const edgeMap = new Map<string, G6Edge>();

  graph.edges.forEach((edge) => {
    if (edge.jobs.length === 0) {
      const edgeId = `${edge.src}->${edge.dst}`;
      edgeMap.set(edgeId, {
        id: edgeId,
        source: String(edge.src),
        target: String(edge.dst),
        data: { jobs: [], isCollapsedLink: false },
      });
      return;
    }

    edge.jobs.forEach((job) => {
      const jobNodeId = `job:${job.jobId}`;
      if (!jobNodes.has(job.jobId)) {
        const outputs = Array.from(jobOutputs.get(job.jobId)?.values() ?? []);
        const hasMultipleDestinations = outputs.length > 1;
        jobNodes.set(job.jobId, {
          id: jobNodeId,
          data: {
            ...job,
            lastRowCount: hasMultipleDestinations ? null : job.lastRowCount,
            lastBytes: hasMultipleDestinations ? null : job.lastBytes,
            lastRunAt: hasMultipleDestinations ? null : job.lastRunAt,
            outputs,
            isJobNode: true,
            isRoot: false,
            isCollapsedPlaceholder: false,
            impactHighlighted: false,
          },
        });
      }

      const inputEdgeId = `${edge.src}->${jobNodeId}`;
      if (!edgeMap.has(inputEdgeId)) {
        edgeMap.set(inputEdgeId, {
          id: inputEdgeId,
          source: String(edge.src),
          target: jobNodeId,
          data: { jobs: [job], isCollapsedLink: false },
        });
      }

      const outputEdgeId = `${jobNodeId}->${edge.dst}`;
      if (!edgeMap.has(outputEdgeId)) {
        edgeMap.set(outputEdgeId, {
          id: outputEdgeId,
          source: jobNodeId,
          target: String(edge.dst),
          data: { jobs: [job], isCollapsedLink: false },
        });
      }
    });
  });

  nodes.push(...jobNodes.values());
  const edges = Array.from(edgeMap.values());

  graph.collapsed.forEach((collapsed) => {
    const placeholderId = `collapsed:${collapsed.token}`;
    nodes.push({
      id: placeholderId,
      data: {
        canonicalName: `+${collapsed.hiddenCount}`,
        dwLayer: null,
        isRoot: false,
        isCollapsedPlaceholder: true,
        hiddenCount: collapsed.hiddenCount,
        expandToken: collapsed.token,
        impactHighlighted: false,
      },
    });
    const triggerId = String(collapsed.nodeId);
    const [source, target] =
      collapsed.direction === 'upstream'
        ? [placeholderId, triggerId]
        : [triggerId, placeholderId];
    edges.push({
      id: `collapsed-edge:${collapsed.token}`,
      source,
      target,
      data: { jobs: [], isCollapsedLink: true },
    });
  });

  return { nodes, edges };
}

/**
 * 把作业运行态指标注入 G6 数据（原地修改）：运行中任务节点补 `runtimeLabel`/`recordsWrittenRate`，
 * 其进出边补 `isRunningLink`。是否处于运行中只看 `runningAppId`（来自图快照，与指标轮询是否已
 * 返回无关）；`jobMetrics` 决定的只是标签上具体的进度数字。
 *
 * 抽成纯函数供 `LineageGraph.tsx` 的增量刷新 effect 复用，避免运行态注入逻辑散落在组件里
 * 与建图逻辑重复一份、后续改一处忘改另一处。
 */
export function applyJobMetrics(
  data: G6GraphData,
  jobMetrics: JobMetricsByAppId,
): void {
  const runningJobNodeIds = new Set<string>();
  data.nodes.forEach((node) => {
    if (!node.data.isJobNode || !node.data.runningAppId) return;
    runningJobNodeIds.add(node.id);
    const metrics = jobMetrics[String(node.data.runningAppId)];
    if (metrics) {
      node.data.runtimeLabel = formatRunningJobLabel(metrics);
      node.data.recordsWrittenRate = metrics.recordsWrittenRate;
    }
  });
  data.edges.forEach((edge) => {
    edge.data.isRunningLink =
      !edge.data.isCollapsedLink &&
      (runningJobNodeIds.has(edge.source) || runningJobNodeIds.has(edge.target));
  });
}

/**
 * 把展开某个折叠节点得到的新一轮遍历结果，合并进当前已展示的图数据。
 *
 * 展开结果是从该节点重新发起的一次独立遍历（深度 1），节点/边可能与当前图有重叠，
 * 按 id 去重覆盖即可（同一 generation 下 NodeMeta 不会变）。已展开的占位符必须从
 * collapsed 列表里移除，否则图上会同时出现真实边和它对应的“+N”占位节点。
 */
export function mergeExpansion(
  current: GraphData,
  expansionResult: GraphData,
  expandedToken: string,
): GraphData {
  const nodeMap = new Map(current.nodes.map((node) => [node.id, node]));
  expansionResult.nodes.forEach((node) => {
    nodeMap.set(node.id, node);
  });

  const edgeMap = new Map<string, LogicalEdge>(
    current.edges.map((edge) => [`${edge.src}->${edge.dst}`, edge]),
  );
  expansionResult.edges.forEach((edge) => {
    edgeMap.set(`${edge.src}->${edge.dst}`, edge);
  });

  const collapsed: CollapsedNode[] = [
    ...current.collapsed.filter((c) => c.token !== expandedToken),
    ...expansionResult.collapsed,
  ];

  return {
    nodes: Array.from(nodeMap.values()),
    edges: Array.from(edgeMap.values()),
    collapsed,
    truncated: collapsed.length > 0,
  };
}
