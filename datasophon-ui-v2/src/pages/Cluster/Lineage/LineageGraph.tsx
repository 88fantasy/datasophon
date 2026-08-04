import { Graph, type IElementEvent } from '@antv/g6';
import { history, useIntl, useParams } from '@umijs/max';
import {
  Alert,
  Button,
  Empty,
  Input,
  message,
  Segmented,
  Select,
  Space,
  Spin,
  Switch,
} from 'antd';
import { useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import FreshnessAlert from './FreshnessAlert';
import JobDetailDrawer from './JobDetailDrawer';
import { formatJobNodeLabel } from './lineageFormatters';
import { mergeExpansion, toG6Data } from './lineageGraphData';
import { getGraph, getImpact, listTables } from './service';
import type {
  GraphData,
  GraphJob,
  LineageDirection,
  SnapshotFreshness,
  SourceFreshness,
} from './service';

const LAYER_FILL: Record<string, string> = {
  CDC: '#fafafa',
  ODS: '#e6f4ff',
  DWD: '#f0f5ff',
  DIM: '#f9f0ff',
  DWS: '#e6fffb',
  ADS: '#f6ffed',
  TMP: '#fff7e6',
};

function httpStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number } })?.response?.status;
}

function errorMessageOf(error: unknown, fallback: string): string {
  return (
    (error as { response?: { data?: { errorMessage?: string } } })?.response
      ?.data?.errorMessage ?? fallback
  );
}

const LineageGraph: React.FC = () => {
  const clusterCtx = useContext(ClusterContext);
  if (!clusterCtx) {
    throw new Error(
      'ClusterContext not found — Lineage graph must be rendered inside ClusterLayout',
    );
  }
  const { clusterId } = clusterCtx;
  const { nodeId } = useParams<{ nodeId: string }>();
  const rootNodeId = Number(nodeId);
  const intl = useIntl();
  const t = useCallback(
    (id: string, defaultMessage: string) =>
      intl.formatMessage({ id, defaultMessage }),
    [intl],
  );

  const [depth, setDepth] = useState(2);
  const [direction, setDirection] = useState<LineageDirection>('both');
  const [impactMode, setImpactMode] = useState(false);
  const [graphData, setGraphData] = useState<GraphData>();
  const [freshness, setFreshness] = useState<{
    snapshot: SnapshotFreshness;
    sourceFreshness: SourceFreshness;
  }>();
  const [loading, setLoading] = useState(false);
  const [impactUnavailable, setImpactUnavailable] = useState(false);
  const [selectedJobs, setSelectedJobs] = useState<GraphJob[]>();

  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph>(undefined);

  const fetchRoot = useCallback(async () => {
    if (!clusterId || !rootNodeId) return;
    setLoading(true);
    setImpactUnavailable(false);
    try {
      if (impactMode) {
        const res = await getImpact(
          { clusterId, rootNodeId, depth },
          { skipErrorHandler: true },
        );
        setGraphData(res.data);
        setFreshness({
          snapshot: res.snapshot,
          sourceFreshness: res.sourceFreshness,
        });
      } else {
        const res = await getGraph({ clusterId, rootNodeId, depth, direction });
        setGraphData(res.data);
        setFreshness({
          snapshot: res.snapshot,
          sourceFreshness: res.sourceFreshness,
        });
      }
    } catch (error) {
      if (impactMode && httpStatus(error) === 503) {
        setImpactUnavailable(true);
        setGraphData(undefined);
      } else if (impactMode) {
        message.error(
          errorMessageOf(error, t('pages.lineage.graph.loadFailed', '加载失败')),
        );
      }
      // 非 impact 模式没有传 skipErrorHandler，默认错误处理已经弹过 toast，这里不重复提示
    } finally {
      setLoading(false);
    }
    // t 故意不入依赖数组：它只影响错误提示文案，不应该在 intl 每次渲染返回新引用时
    // 触发重新拉取（曾经因此在测试里造成无限刷新循环，真实 intl 实现是否稳定引用不能假设）。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clusterId, rootNodeId, depth, direction, impactMode]);

  useEffect(() => {
    fetchRoot();
  }, [fetchRoot]);

  const handleExpand = useCallback(
    async (token: string) => {
      if (!clusterId || !rootNodeId) return;
      try {
        const res = await getGraph(
          { clusterId, rootNodeId, expand: token },
          { skipErrorHandler: true },
        );
        setGraphData((prev) =>
          prev ? mergeExpansion(prev, res.data, token) : res.data,
        );
        setFreshness({
          snapshot: res.snapshot,
          sourceFreshness: res.sourceFreshness,
        });
      } catch (error) {
        if (httpStatus(error) === 409) {
          message.warning(
            t('pages.lineage.graph.staleExpand', '血缘已更新，正在刷新...'),
          );
          fetchRoot();
        } else {
          message.error(
            errorMessageOf(error, t('pages.lineage.graph.expandFailed', '展开失败')),
          );
        }
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    },
    [clusterId, rootNodeId, fetchRoot],
  );

  // 影响分析只看下游；root 本身不算"受影响"，排除掉
  const impactHighlightIds = useMemo(() => {
    if (!impactMode || !graphData) return undefined;
    return new Set(
      graphData.nodes.map((n) => n.id).filter((id) => id !== rootNodeId),
    );
  }, [impactMode, graphData, rootNodeId]);

  useEffect(() => {
    if (!graphData || graphData.nodes.length === 0) {
      graphRef.current?.destroy();
      graphRef.current = undefined;
      return;
    }
    const data = toG6Data(graphData, rootNodeId, impactHighlightIds);
    const renderGraph = (graph: Graph) => {
      void Promise.resolve(graph.render()).then(() => graph.fitView());
    };
    if (graphRef.current) {
      graphRef.current.setData(data);
      renderGraph(graphRef.current);
      return;
    }
    if (!containerRef.current) return;
    const graph = new Graph({
      container: containerRef.current,
      padding: 24,
      data,
      node: {
        type: (d) => (d.data?.isJobNode ? 'diamond' : 'rect'),
        style: {
          size: (d) => (d.data?.isJobNode ? [200, 84] : [180, 52]),
          radius: 8,
          fill: (d) =>
            d.data?.isCollapsedPlaceholder
              ? '#ffffff'
              : d.data?.isJobNode
                ? '#f9f0ff'
              : d.data?.impactHighlighted
                ? '#fff1f0'
                : (LAYER_FILL[String(d.data?.dwLayer ?? '')] ?? '#fafafa'),
          stroke: (d) =>
            d.data?.isRoot
              ? '#faad14'
              : d.data?.isJobNode
                ? '#722ed1'
              : d.data?.impactHighlighted
                ? '#ff4d4f'
                : d.data?.isCollapsedPlaceholder
                  ? '#bfbfbf'
                  : '#85a5ff',
          lineDash: (d) =>
            d.data?.isCollapsedPlaceholder ? [4, 4] : undefined,
          lineWidth: (d) => (d.data?.isRoot ? 3 : 1.5),
          cursor: (d) =>
            d.data?.isCollapsedPlaceholder || d.data?.isJobNode
              ? 'pointer'
              : 'default',
          labelText: (d) =>
            d.data?.isJobNode
              ? formatJobNodeLabel(d.data as unknown as GraphJob)
              : String(d.data?.canonicalName ?? d.id),
          labelPlacement: 'center',
          labelWordWrap: true,
          labelMaxWidth: (d) => (d.data?.isJobNode ? 150 : 160),
          labelFontSize: 12,
          labelLineHeight: 18,
        },
      },
      edge: {
        type: 'cubic-horizontal',
        style: {
          endArrow: true,
          lineWidth: 1.5,
          stroke: '#99add1',
          lineDash: (d) => (d.data?.isCollapsedLink ? [4, 4] : undefined),
          cursor: (d) => (d.data?.isCollapsedLink ? 'default' : 'pointer'),
        },
      },
      layout: {
        type: 'antv-dagre',
        rankdir: 'LR',
        nodesep: 16,
        ranksep: 64,
      },
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
    });
    graph.on('node:click', (event: IElementEvent) => {
      const id = event.target?.id;
      if (typeof id !== 'string') return;
      const nodeDatum = graph.getNodeData(id);
      const expandToken = nodeDatum?.data?.expandToken;
      if (typeof expandToken === 'string') {
        handleExpand(expandToken);
        return;
      }
      if (id.startsWith('job:') && nodeDatum?.data?.isJobNode) {
        setSelectedJobs([nodeDatum.data as unknown as GraphJob]);
      }
    });
    graph.on('edge:click', (event: IElementEvent) => {
      const id = event.target?.id;
      if (typeof id !== 'string') return;
      const edgeDatum = graph.getEdgeData(id);
      if (edgeDatum?.data?.isCollapsedLink) return;
      setSelectedJobs((edgeDatum?.data?.jobs as GraphJob[] | undefined) ?? []);
    });
    graphRef.current = graph;
    renderGraph(graph);
  }, [graphData, rootNodeId, impactHighlightIds, handleExpand]);

  useEffect(() => {
    return () => {
      graphRef.current?.destroy();
      graphRef.current = undefined;
    };
  }, []);

  const handleSearch = async (value: string) => {
    const trimmed = value.trim();
    if (!trimmed || !clusterId) return;
    const res = await listTables({ clusterId, keyword: trimmed, size: 1 });
    const match = res.data.list[0];
    if (!match) {
      message.warning(t('pages.lineage.graph.searchNotFound', '未找到匹配的表'));
      return;
    }
    history.push(`/cluster/${clusterId}/lineage/${match.id}`);
  };

  return (
    <div style={{ padding: 16 }}>
      {freshness && (
        <FreshnessAlert
          clusterId={clusterId}
          snapshot={freshness.snapshot}
          sourceFreshness={freshness.sourceFreshness}
          onRebuilt={fetchRoot}
        />
      )}
      <Space wrap style={{ marginBottom: 12 }}>
        <Button onClick={() => history.push(`/cluster/${clusterId}/lineage`)}>
          {t('pages.lineage.graph.backToList', '返回清单')}
        </Button>
        <span>{t('pages.lineage.graph.depth', '深度')}</span>
        <Select
          value={depth}
          style={{ width: 80 }}
          onChange={setDepth}
          options={[1, 2, 3, 4, 5].map((d) => ({ value: d, label: d }))}
        />
        <span>{t('pages.lineage.graph.direction', '方向')}</span>
        <Segmented
          value={impactMode ? 'downstream' : direction}
          disabled={impactMode}
          onChange={(value) => setDirection(value as LineageDirection)}
          options={[
            { label: t('pages.lineage.graph.upstream', '上游'), value: 'upstream' },
            { label: t('pages.lineage.graph.downstream', '下游'), value: 'downstream' },
            { label: t('pages.lineage.graph.both', '双向'), value: 'both' },
          ]}
        />
        <span>{t('pages.lineage.graph.impactMode', '影响分析')}</span>
        <Switch checked={impactMode} onChange={setImpactMode} />
        <Input.Search
          placeholder={t(
            'pages.lineage.graph.searchPlaceholder',
            '搜索表名切换根节点',
          )}
          style={{ width: 240 }}
          allowClear
          onSearch={handleSearch}
        />
      </Space>
      {impactUnavailable && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          title={t(
            'pages.lineage.graph.impactUnavailable',
            '快照陈旧，影响分析暂不可用，请先重建快照',
          )}
        />
      )}
      {graphData?.truncated && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          title={t(
            'pages.lineage.graph.truncated',
            '部分邻居节点超出单次展示上限，已折叠为 "+N"，点击可继续展开',
          )}
        />
      )}
      <Spin spinning={loading}>
        {graphData && graphData.nodes.length === 0 ? (
          <Empty style={{ padding: '80px 0' }} />
        ) : (
          <div ref={containerRef} style={{ height: 560 }} />
        )}
      </Spin>
      <JobDetailDrawer
        clusterId={clusterId}
        jobs={selectedJobs ?? []}
        open={Boolean(selectedJobs)}
        onClose={() => setSelectedJobs(undefined)}
      />
    </div>
  );
};

export default LineageGraph;
