import type { CollapsedNode, GraphData, GraphJob, LogicalEdge } from './service';

export interface G6NodeData {
  canonicalName: string;
  dwLayer: string | null;
  isRoot: boolean;
  isCollapsedPlaceholder: boolean;
  hiddenCount?: number;
  expandToken?: string;
  impactHighlighted: boolean;
  // G6 的 NodeData.data 要求 Record<string, unknown> 索引签名
  [key: string]: unknown;
}

export interface G6EdgeData {
  jobs: GraphJob[];
  isCollapsedLink: boolean;
  [key: string]: unknown;
}

export interface G6Node {
  id: string;
  data: G6NodeData;
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
      dwLayer: node.dwLayer,
      isRoot: node.id === rootNodeId,
      isCollapsedPlaceholder: false,
      impactHighlighted: impactHighlightIds?.has(node.id) ?? false,
    },
  }));
  const edges: G6Edge[] = graph.edges.map((edge) => ({
    id: `${edge.src}->${edge.dst}`,
    source: String(edge.src),
    target: String(edge.dst),
    data: { jobs: edge.jobs, isCollapsedLink: false },
  }));

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
