import { Graph, type GraphData } from '@antv/g6';
import { useIntl } from '@umijs/max';
import { Alert, Button } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useFillViewportHeight } from '@/pages/Cluster/_shared/useFillViewportHeight';
import { FLOWING_LINEAGE_EDGE } from '@/pages/Cluster/Lineage/flowingLineageEdge';
import {
  formatOutputSize,
  formatRate,
  formatRows,
  shortDatasetName,
} from './formatters';

// 结构签名：只看节点/边的拓扑（taskCode 集合 + from-to 集合），不含 state/metrics 等
// 会随每轮轮询变化的字段。用于区分“数据更新”（可以增量刷新，不打扰画布）与
// “结构变化”（节点/边增删，需要用户确认后才重建画布）。
function structureKey(dag: DATASOPHON.DsDag): string {
  const nodeIds = dag.nodes
    .map((node) => node.taskCode)
    .sort((a, b) => a - b)
    .join(',');
  const edgeIds = dag.edges
    .map((edge) => `${edge.from}-${edge.to}`)
    .sort()
    .join(',');
  return `${nodeIds}|${edgeIds}`;
}

interface DsDagGraphProps {
  dag: DATASOPHON.DsDag;
}

const STATE_COLORS: Record<string, string> = {
  SUCCESS: '#52c41a',
  RUNNING_EXECUTION: '#1677ff',
  FAILURE: '#ff4d4f',
  KILL: '#8c8c8c',
  PAUSE: '#faad14',
  STOP: '#8c8c8c',
  SUBMITTED_SUCCESS: '#13c2c2',
};

function nodeLabel(
  node: DATASOPHON.DsDagNode,
  statusLabel: string,
  approximateLabel: string,
  rowsLabel: string,
  processedLabel: string,
  itemsLabel: string,
  notBoundLabel: string,
  jobEndedLabel: string,
): string {
  const header = `${node.name}\n${node.taskType} · ${statusLabel}`;
  if (node.metrics?.kind === 'STREAM') {
    const lines = [
      `${approximateLabel} ${formatRate(node.metrics.rowsPerSecond)}`,
    ];
    if (node.metrics.processedApprox != null) {
      lines.push(
        `${processedLabel} ${formatRows(node.metrics.processedApprox)} ${itemsLabel}`,
      );
    }
    return `${header}\n${lines.join('\n')}`;
  }
  if (node.metrics?.kind === 'BATCH' && node.metrics.outputs?.length) {
    const lines = node.metrics.outputs
      .slice(0, 2)
      .map(
        (output) =>
          `${shortDatasetName(output.name)}  ${formatRows(output.rowCount)} ${rowsLabel} / ${formatOutputSize(output.size)}`,
      );
    if (node.metrics.outputs.length > 2) {
      lines.push(`+${node.metrics.outputs.length - 2}`);
    }
    return `${header}\n${lines.join('\n')}`;
  }
  if (node.metricsError === 'NOT_BOUND') {
    return `${header}\n— ${notBoundLabel}`;
  }
  return node.metricsError === 'JOB_ENDED'
    ? `${header}\n— ${jobEndedLabel}`
    : `${header}\n—`;
}

const DsDagGraph: React.FC<DsDagGraphProps> = ({ dag }) => {
  const intl = useIntl();
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph>(undefined);
  // appliedDag 是当前真正渲染在画布上的结构；结构没变时随每轮轮询推进（用于给下面的
  // 数据增量刷新 effect 提供“哪些节点仍在画布上”），结构变了时先挂起到 pendingDag，
  // 等用户点“刷新画布”才真正应用。
  const [appliedDag, setAppliedDag] = useState(dag);
  const [pendingDag, setPendingDag] = useState<DATASOPHON.DsDag>();
  // 只有这个签名变化才应该触发“建图”effect 重建画布——appliedDag 对象引用本身
  // 在“结构不变、数据更新”时也会变，不能直接拿它当依赖，否则每轮轮询都会重建。
  const [appliedStructureSignature, setAppliedStructureSignature] = useState(
    () => structureKey(dag),
  );

  useEffect(() => {
    const newKey = structureKey(dag);
    if (newKey === appliedStructureSignature) {
      setAppliedDag(dag);
      setPendingDag(undefined);
    } else {
      setPendingDag(dag);
    }
  }, [dag, appliedStructureSignature]);

  const applyPendingStructure = () => {
    if (!pendingDag) return;
    setAppliedStructureSignature(structureKey(pendingDag));
    setAppliedDag(pendingDag);
    setPendingDag(undefined);
  };

  const labels = useMemo(
    () => ({
      approximate: intl.formatMessage({ id: 'dsWorkflow.metric.approximate' }),
      rows: intl.formatMessage({ id: 'dsWorkflow.metric.rows' }),
      processed: intl.formatMessage({ id: 'dsWorkflow.metric.processed' }),
      items: intl.formatMessage({ id: 'dsWorkflow.metric.items' }),
      notBound: intl.formatMessage({ id: 'dsWorkflow.error.notBound' }),
      jobEnded: intl.formatMessage({ id: 'dsWorkflow.status.jobEnded' }),
      status: (state?: string) =>
        intl.formatMessage({
          id: `dsWorkflow.state.${state ?? 'UNKNOWN'}`,
          defaultMessage: state || '—',
        }),
    }),
    [intl],
  );
  const graphHeight = useFillViewportHeight(
    containerRef,
    [appliedStructureSignature, labels],
    {
      minHeight: 580,
      onHeightChange: () => graphRef.current?.resize(),
    },
  );

  // 建图 effect：只在“结构”真正被应用时（首次渲染，或用户确认了新结构）触发，
  // 会 destroy 旧图重建、fitView 重置视角。故意不依赖每轮轮询的 dag 本身。
  useEffect(() => {
    if (!containerRef.current) return;
    const locations = new Map(
      (appliedDag.locations ?? []).map((location) => [
        location.taskCode,
        location,
      ]),
    );
    const hasPinnedLayout = appliedDag.nodes.every((node) =>
      locations.has(node.taskCode),
    );
    const data = {
      nodes: appliedDag.nodes.map((node) => {
        const location = locations.get(node.taskCode);
        return {
          id: String(node.taskCode),
          data: node,
          style: location ? { x: location.x, y: location.y } : undefined,
        };
      }),
      edges: appliedDag.edges.map((edge) => ({
        id: `${edge.from}-${edge.to}`,
        source: String(edge.from),
        target: String(edge.to),
        data: {
          streaming:
            appliedDag.nodes.find((node) => node.taskCode === edge.from)
              ?.flowType === 'STREAM',
        },
      })),
    };
    const graph = new Graph({
      container: containerRef.current,
      autoResize: true,
      padding: 28,
      data: data as unknown as GraphData,
      node: {
        type: 'rect',
        style: {
          size: [290, 145],
          radius: 10,
          fill: '#ffffff',
          stroke: (datum) =>
            STATE_COLORS[String(datum.data?.state ?? '')] ?? '#bfbfbf',
          lineWidth: 4,
          labelText: (datum) => {
            const node = datum.data as unknown as DATASOPHON.DsDagNode;
            return nodeLabel(
              node,
              labels.status(node.state),
              labels.approximate,
              labels.rows,
              labels.processed,
              labels.items,
              labels.notBound,
              labels.jobEnded,
            );
          },
          labelPlacement: 'center',
          labelWordWrap: true,
          labelMaxWidth: 260,
          labelMaxLines: 7,
          labelFontSize: 12,
          labelLineHeight: 19,
        },
      },
      edge: {
        type: (datum) =>
          datum.data?.streaming ? FLOWING_LINEAGE_EDGE : 'cubic-horizontal',
        style: {
          endArrow: true,
          lineWidth: 1.5,
          stroke: (datum) => (datum.data?.streaming ? '#1677ff' : '#99add1'),
          lineDash: (datum) => (datum.data?.streaming ? [8, 4] : undefined),
        },
      },
      layout: hasPinnedLayout
        ? undefined
        : { type: 'antv-dagre', rankdir: 'LR', nodesep: 24, ranksep: 76 },
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
    });
    graphRef.current = graph;
    void Promise.resolve(graph.render()).then(() =>
      graph.fitView({ when: 'overflow' }),
    );
    return () => {
      graph.destroy();
      graphRef.current = undefined;
    };
    // 故意只依赖结构签名，不依赖 appliedDag 对象本身——appliedDag 的引用在“结构不变、
    // 数据更新”时也会变，若直接依赖它会导致每轮轮询都 destroy 重建、重置用户视角。
  }, [appliedStructureSignature, labels]);

  // 数据增量刷新：结构没变时，每轮轮询只更新已存在节点的 state/metrics，
  // 不 destroy/render/fitView，保留用户当前的缩放与拖动位置。
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph) return;
    const appliedIds = new Set(appliedDag.nodes.map((node) => node.taskCode));
    const nodeUpdates = dag.nodes
      .filter((node) => appliedIds.has(node.taskCode))
      .map((node) => ({
        id: String(node.taskCode),
        data: node as unknown as Record<string, unknown>,
      }));
    if (nodeUpdates.length === 0) return;
    graph.updateNodeData(nodeUpdates);
    void graph.draw();
  }, [dag, appliedDag]);

  return (
    <>
      {pendingDag ? (
        <Alert
          type="info"
          showIcon
          message={intl.formatMessage({
            id: 'dsWorkflow.dag.structureChanged',
          })}
          action={
            <Button size="small" onClick={applyPendingStructure}>
              {intl.formatMessage({ id: 'dsWorkflow.dag.refreshCanvas' })}
            </Button>
          }
          style={{ marginBottom: 8 }}
        />
      ) : null}
      <div ref={containerRef} style={{ height: graphHeight, width: '100%' }} />
    </>
  );
};

export default DsDagGraph;
