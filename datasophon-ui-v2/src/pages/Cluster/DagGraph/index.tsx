/**
 * DAG 图全屏页（切片 6b）
 * 路由：/cluster/:clusterId/dag/:dagId（layout: false，新窗口全屏）
 * 移植自 datasophon-ui/src/components/DagModal/index.tsx
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from '@umijs/max';
import { Graph, IS_SAFARI } from '@antv/x6';
import { blue, gold, green, grey, red } from '@ant-design/colors';
import { Button, message, Modal, Spin } from 'antd';
import { request } from '@umijs/max';

import DataProcessingDagNode from './DataProcessingDagNode';
import dagEvent, { dagUiEvent } from './dagEvent';
import { invokeGenPort, invokeGenSourceAndTarget } from './antvUtils';
import {
  T_CANCEL,
  T_FAILED,
  T_PENDING,
  T_RUNNING,
  T_SUCCESS,
} from './dagStatus';
import { getDagGraph, redeployDag } from '@/services/dag';

// 注册 x6 节点 / 边 / 连接器（全局只需一次）
DataProcessingDagNode.invokeInit();

// ── 边动画配置（按源节点状态） ────────────────────────────────────────────────

const LINE_STATUS: Record<string, Record<string, string | number>> = {
  [T_SUCCESS]: { 'line/stroke': green.primary!, 'line/strokeDasharray': 0, 'line/style/animation': '' },
  [T_FAILED]: { 'line/stroke': red.primary!, 'line/strokeDasharray': 0, 'line/style/animation': '' },
  [T_CANCEL]: { 'line/stroke': gold.primary!, 'line/strokeDasharray': 0, 'line/style/animation': '' },
  [T_PENDING]: { 'line/stroke': grey.primary!, 'line/strokeDasharray': 0, 'line/style/animation': '' },
  [T_RUNNING]: {
    'line/stroke': blue.primary!,
    'line/strokeDasharray': 5,
    'line/style/animation': 'running-line 30s infinite linear',
  },
};

// ── 数据转换：API 返回 → x6 nodes/edges ──────────────────────────────────────

function invokeTransferData(data: DATASOPHON.DagGraph) {
  const { nodes = [], edges = [], clusterId, archType } = data;

  const mapNodes: any[] = [];
  const mapEdges: any[] = [];
  const nodeMap: Record<string, any> = {};
  const nodeStatusMap: Record<string, Record<string, any>> = {};

  nodes.forEach((val) => {
    const id = String(val.id);
    const nodeVal = { ...val, id, clusterId };
    const x6Node = {
      id,
      shape: DataProcessingDagNode.shape,
      ports: invokeGenPort({ id }),
      data: { data: nodeVal, archType },
      type: 'OUTPUT',
      archType,
    };
    nodeMap[id] = nodeVal;
    if (!nodeStatusMap[val.status]) nodeStatusMap[val.status] = {};
    nodeStatusMap[val.status][id] = nodeVal;
    mapNodes.push(x6Node);
  });

  edges.forEach((val) => {
    const id = String(val.id);
    const start = val.start != null ? String(val.start) : undefined;
    const end = val.end != null ? String(val.end) : undefined;
    mapEdges.push({
      id,
      shape: DataProcessingDagNode.edgeName,
      ...invokeGenSourceAndTarget(start, end),
    });
  });

  return { nodes: mapNodes, edges: mapEdges, nodeMap, nodeStatusMap };
}

// ── 简单 LR 拓扑排布（替代 @antv/layout v2 不兼容 API）────────────────────────
// 通过 BFS 按层级分配 x（层级索引×间距）和 y（同层节点索引×间距）

const NODE_W = 250; // 节点水平间距（宽度 + gap）
const NODE_H = 170; // 节点垂直间距（高度 + gap）

function applyLrLayout(nodes: any[], edges: any[]): any[] {
  const inDegree: Record<string, number> = {};
  const children: Record<string, string[]> = {};
  nodes.forEach((n) => {
    inDegree[n.id] = 0;
    children[n.id] = [];
  });
  edges.forEach((e) => {
    const src = e.data?.source as string | undefined;
    const tgt = e.data?.target as string | undefined;
    if (src && tgt && inDegree[tgt] !== undefined) {
      children[src]?.push(tgt);
      inDegree[tgt]++;
    }
  });

  // BFS 层级分配
  const levels: string[][] = [];
  const visited = new Set<string>();
  let queue = nodes.filter((n) => inDegree[n.id] === 0).map((n) => n.id as string);

  while (queue.length > 0) {
    levels.push(queue);
    for (const id of queue) visited.add(id);
    const next: string[] = [];
    queue.forEach((id) => {
      (children[id] ?? []).forEach((childId) => {
        inDegree[childId]--;
        if (inDegree[childId] === 0 && !visited.has(childId)) {
          next.push(childId);
        }
      });
    });
    queue = next;
  }

  // 将未被访问的节点（存在环时）单独成层
  for (const n of nodes.filter((n) => !visited.has(n.id))) levels.push([n.id]);

  const positions = new Map<string, { x: number; y: number }>();
  levels.forEach((levelNodes, li) => {
    levelNodes.forEach((id, ni) => {
      positions.set(id, { x: li * NODE_W, y: ni * NODE_H });
    });
  });

  return nodes.map((n) => ({ ...n, ...(positions.get(n.id) ?? {}) }));
}

// ── 组件 ────────────────────────────────────────────────────────────────────

const DagGraphPage: React.FC = () => {
  const { clusterId: clusterIdStr, dagId } = useParams<{
    clusterId: string;
    dagId: string;
  }>();
  const clusterId = Number(clusterIdStr ?? 0);

  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const pollingRef = useRef<number | undefined>(undefined);
  const nodeMapRef = useRef<Record<string, any>>({});

  // 调度日志 Modal 状态
  const [scheduleLog, setScheduleLog] = useState<{ open: boolean; loading: boolean; text: string }>(
    { open: false, loading: false, text: '' },
  );

  // ── 边动画更新 ──────────────────────────────────────────────────────────────
  const updateAnimate = useCallback(() => {
    const graph = graphRef.current;
    if (!graph) return;
    graph.getEdges().forEach((edge) => {
      const src = edge.getData()?.source as string | undefined;
      const srcNode = src ? nodeMapRef.current[src] : undefined;
      const statusAttrs = srcNode ? LINE_STATUS[srcNode.status] : undefined;
      if (statusAttrs) {
        Object.entries(statusAttrs).forEach(([k, v]) => {
          edge.attr(k, v);
        });
      }
    });
  }, []);

  // ── 停止轮询 ───────────────────────────────────────────────────────────────
  const cancelPolling = useCallback(() => {
    if (pollingRef.current !== undefined) {
      clearTimeout(pollingRef.current);
      pollingRef.current = undefined;
    }
  }, []);

  // ── 初始化 x6 Graph 实例 ───────────────────────────────────────────────────
  const invokeInitGraph = useCallback(() => {
    if (!containerRef.current) return;
    const container = containerRef.current;
    const graph = new Graph({
      container,
      width: container.clientWidth || window.innerWidth,
      height: container.clientHeight || window.innerHeight,
      panning: {
        enabled: true,
        eventTypes: ['leftMouseDown', 'mouseWheel'],
      },
      mousewheel: {
        enabled: true,
        modifiers: 'ctrl',
        factor: 1.1,
        maxScale: 1.5,
        minScale: 0.5,
      },
      connecting: {
        snap: true,
        allowBlank: false,
        allowLoop: false,
        highlight: true,
        sourceAnchor: { name: 'left', args: { dx: IS_SAFARI ? 4 : 8 } },
        targetAnchor: { name: 'right', args: { dx: IS_SAFARI ? 4 : -8 } },
      },
    });
    graphRef.current = graph;
  }, []);

  // ── 数据拉取 + 渲染（update=false: 首次建图；update=true: 仅刷新状态） ────────
  const invokeLoad = useCallback(
    async (update: boolean) => {
      if (!dagId) return;
      try {
        const res = await getDagGraph(clusterId, dagId);
        const raw = (res as any)?.data ?? res;
        if (!raw) return;

        const transferred = invokeTransferData(raw as DATASOPHON.DagGraph);
        nodeMapRef.current = transferred.nodeMap;

        if (!update) {
          // 首次：LR 拓扑布局后整体渲染
          const positionedNodes = applyLrLayout(transferred.nodes, transferred.edges);
          dagEvent.emit(dagUiEvent.updateNodeSize);
          graphRef.current?.fromJSON({ nodes: positionedNodes, edges: transferred.edges });
        } else {
          // 后续：只更新数据 + 边动画
          dagEvent.emit(dagUiEvent.updateNodeData, transferred.nodeMap);
        }

        updateAnimate();

        // 存在进行中节点时继续轮询
        const hasPending =
          Object.keys(transferred.nodeStatusMap[T_PENDING] ?? {}).length > 0;
        const hasRunning =
          Object.keys(transferred.nodeStatusMap[T_RUNNING] ?? {}).length > 0;

        if (hasPending || hasRunning) {
          pollingRef.current = window.setTimeout(() => invokeLoad(true), 3000);
        }
      } catch (err) {
        console.error('DagGraph load error:', err);
      }
    },
    [clusterId, dagId, updateAnimate],
  );

  // ── 重新运行 ───────────────────────────────────────────────────────────────
  const onRedeployClick = useCallback(async () => {
    if (!dagId) return;
    try {
      await redeployDag(clusterId, dagId);
      message.success('已触发重新运行');
      cancelPolling();
      await invokeLoad(false);
    } catch {
      message.error('重新运行失败');
    }
  }, [clusterId, dagId, cancelPolling, invokeLoad]);

  // ── 调度日志 ───────────────────────────────────────────────────────────────
  const onLogClick = useCallback(async () => {
    setScheduleLog({ open: true, loading: true, text: '' });
    try {
      const res = await request<any>('/ddh/api/log/getScheduleLog', {
        method: 'GET',
        params: { clusterId, dagId },
      });
      const text: string =
        typeof res === 'string'
          ? res
          : typeof res?.data === 'string'
            ? res.data
            : JSON.stringify(res?.data ?? res, null, 2);
      setScheduleLog((p) => ({ ...p, loading: false, text }));
    } catch {
      setScheduleLog((p) => ({ ...p, loading: false, text: '获取调度日志失败' }));
    }
  }, [clusterId, dagId]);

  // ── 生命周期 ───────────────────────────────────────────────────────────────
  useEffect(() => {
    invokeInitGraph();
    invokeLoad(false);
    return () => {
      cancelPolling();
      graphRef.current?.dispose();
    };
  }, [invokeInitGraph, invokeLoad, cancelPolling]);

  return (
    <div style={{ position: 'relative', width: '100vw', height: '100vh', overflow: 'hidden', background: '#f5f5f5' }}>
      {/* x6 画布容器 */}
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />

      {/* 操作按钮（右上角固定） */}
      <div
        style={{
          position: 'fixed',
          top: 20,
          right: 20,
          display: 'flex',
          gap: 10,
          zIndex: 100,
        }}
      >
        <Button onClick={onLogClick}>调度日志</Button>
        <Button type="primary" onClick={onRedeployClick}>
          重新运行
        </Button>
      </div>

      {/* 调度日志 Modal */}
      <Modal
        title="调度日志"
        open={scheduleLog.open}
        onCancel={() => setScheduleLog((p) => ({ ...p, open: false }))}
        footer={null}
        width={800}
        destroyOnClose
      >
        {scheduleLog.loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : (
          <pre
            style={{
              maxHeight: 480,
              overflow: 'auto',
              background: '#1e1e1e',
              color: '#d4d4d4',
              padding: 16,
              borderRadius: 4,
              fontSize: 12,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
            }}
          >
            {scheduleLog.text || '（暂无日志）'}
          </pre>
        )}
      </Modal>
    </div>
  );
};

export default DagGraphPage;
