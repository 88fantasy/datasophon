import { Graph, type GraphData } from '@antv/g6';
import { useIntl } from '@umijs/max';
import { useEffect, useMemo, useRef } from 'react';
import { FLOWING_LINEAGE_EDGE } from '@/pages/Cluster/Lineage/flowingLineageEdge';
import {
  formatOutputSize,
  formatRate,
  formatRows,
  shortDatasetName,
} from './formatters';

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
): string {
  const header = `${node.name}\n${node.taskType} · ${statusLabel}`;
  if (node.metrics?.kind === 'STREAM') {
    return `${header}\n${approximateLabel} ${formatRate(node.metrics.rowsPerSecond)}`;
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
  return `${header}\n—`;
}

const DsDagGraph: React.FC<DsDagGraphProps> = ({ dag }) => {
  const intl = useIntl();
  const containerRef = useRef<HTMLDivElement>(null);
  const labels = useMemo(
    () => ({
      approximate: intl.formatMessage({ id: 'dsWorkflow.metric.approximate' }),
      rows: intl.formatMessage({ id: 'dsWorkflow.metric.rows' }),
      status: (state?: string) =>
        intl.formatMessage({
          id: `dsWorkflow.state.${state ?? 'UNKNOWN'}`,
          defaultMessage: state || '—',
        }),
    }),
    [intl],
  );

  useEffect(() => {
    if (!containerRef.current) return;
    const locations = new Map(
      (dag.locations ?? []).map((location) => [location.taskCode, location]),
    );
    const hasPinnedLayout = dag.nodes.every((node) =>
      locations.has(node.taskCode),
    );
    const data = {
      nodes: dag.nodes.map((node) => {
        const location = locations.get(node.taskCode);
        return {
          id: String(node.taskCode),
          data: node,
          style: location ? { x: location.x, y: location.y } : undefined,
        };
      }),
      edges: dag.edges.map((edge) => ({
        id: `${edge.from}-${edge.to}`,
        source: String(edge.from),
        target: String(edge.to),
        data: {
          streaming:
            dag.nodes.find((node) => node.taskCode === edge.from)?.flowType ===
            'STREAM',
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
          size: [290, 126],
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
            );
          },
          labelPlacement: 'center',
          labelWordWrap: true,
          labelMaxWidth: 260,
          labelMaxLines: 6,
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
    void Promise.resolve(graph.render()).then(() =>
      graph.fitView({ when: 'overflow' }),
    );
    return () => {
      graph.destroy();
    };
  }, [dag, labels]);

  return <div ref={containerRef} style={{ height: 580, width: '100%' }} />;
};

export default DsDagGraph;
