import { Graph, type ElementDatum, type IElementEvent } from '@antv/g6';
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
import {
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import ClusterContext from '@/context/ClusterContext';
import FreshnessAlert from './FreshnessAlert';
import { FLOWING_LINEAGE_EDGE } from './flowingLineageEdge';
import JobDetailDrawer from './JobDetailDrawer';
import { formatJobNodeLabel } from './lineageFormatters';
import { applyJobMetrics, mergeExpansion, toG6Data } from './lineageGraphData';
import type { JobOutputStat } from './lineageGraphData';
import { getGraph, getImpact, getJobMetrics, listTables } from './service';
import type {
  GraphData,
  GraphJob,
  JobMetricsByAppId,
  LineageDirection,
  SnapshotFreshness,
  SourceFreshness,
} from './service';

/** Drawer 需要的作业信息：单输出走原有字段，多输出额外带 outputs 按目标表拆分统计。 */
type SelectedJob = GraphJob & { outputs?: JobOutputStat[] };

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

  const [depth, setDepth] = useState(3);
  const [direction, setDirection] = useState<LineageDirection>('both');
  const [impactMode, setImpactMode] = useState(false);
  const [graphData, setGraphData] = useState<GraphData>();
  const [freshness, setFreshness] = useState<{
    snapshot: SnapshotFreshness;
    sourceFreshness: SourceFreshness;
  }>();
  const [loading, setLoading] = useState(false);
  const [impactUnavailable, setImpactUnavailable] = useState(false);
  const [selectedJobs, setSelectedJobs] = useState<SelectedJob[]>();
  const [jobMetrics, setJobMetrics] = useState<JobMetricsByAppId>({});

  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph>(undefined);
  const [graphHeight, setGraphHeight] = useState(560);

  // 图容器高度按视口剩余空间动态计算，而非固定 560px，避免大屏下图区域下方留白
  // （上方的 clusterBar / 提示 Alert 数量会变化，故不用固定 calc(100vh - Npx)，
  // 而是实测容器实际的 top 偏移）。
  useLayoutEffect(() => {
    const recompute = () => {
      if (!containerRef.current) return;
      const top = containerRef.current.getBoundingClientRect().top;
      setGraphHeight(Math.max(480, window.innerHeight - top - 24));
    };
    recompute();
    window.addEventListener('resize', recompute);
    return () => window.removeEventListener('resize', recompute);
  }, [freshness, impactUnavailable, graphData?.truncated]);

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

  const runningAppIds = useMemo(
    () =>
      Array.from(
        new Set(
          (graphData?.edges ?? []).flatMap((edge) =>
            edge.jobs.flatMap((job) =>
              job.runningAppId ? [job.runningAppId] : [],
            ),
          ),
        ),
      ).sort(),
    [graphData],
  );

  useEffect(() => {
    if (!clusterId || runningAppIds.length === 0) {
      setJobMetrics({});
      return;
    }

    let cancelled = false;
    const refresh = async () => {
      try {
        const metrics = await getJobMetrics(clusterId, runningAppIds);
        if (!cancelled) setJobMetrics(metrics);
      } catch {
        // 轮询失败时保留上次采样，下一周期自动重试，避免运行态图闪回历史标签。
      }
    };

    void refresh();
    const timer = window.setInterval(() => void refresh(), 15_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [clusterId, runningAppIds]);

  useEffect(() => {
    if (!graphData || graphData.nodes.length === 0) {
      graphRef.current?.destroy();
      graphRef.current = undefined;
      return;
    }
    // 本 effect 只管图的"结构"（节点/边集合），故意不依赖 jobMetrics——运行态指标由下面
    // 单独的 effect 增量刷新。两者若合成一个 effect，每 15 秒的指标轮询都会触发
    // setData+render()+fitView()，把用户手动缩放/拖动过的视角强制拉回默认状态
    // （P1：runningAppId 存在与否只看 graphData 本身，这里天然已经是最新值）。
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
      autoResize: true,
      padding: 24,
      data,
      node: {
        type: (d) => (d.data?.isJobNode ? 'diamond' : 'rect'),
        style: {
          size: (d) => (d.data?.isJobNode ? [240, 84] : [180, 52]),
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
              ? formatJobNodeLabel(
                  d.data as unknown as GraphJob,
                  typeof d.data.runtimeLabel === 'string'
                    ? d.data.runtimeLabel
                    : undefined,
                )
              : String(d.data?.tableName || d.data?.canonicalName || d.id),
          labelPlacement: 'center',
          labelWordWrap: true,
          labelMaxWidth: (d) => (d.data?.isJobNode ? 200 : 160),
          labelFontSize: 12,
          labelLineHeight: 18,
        },
      },
      edge: {
        type: (d) =>
          d.data?.isRunningLink
            ? FLOWING_LINEAGE_EDGE
            : 'cubic-horizontal',
        style: {
          endArrow: true,
          lineWidth: 1.5,
          stroke: (d) => (d.data?.isRunningLink ? '#722ed1' : '#99add1'),
          lineDash: (d) =>
            d.data?.isCollapsedLink
              ? [4, 4]
              : d.data?.isRunningLink
                ? [8, 4]
                : undefined,
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
      plugins: [
        {
          type: 'tooltip',
          trigger: 'hover',
          getContent: async (
            _event: IElementEvent,
            items: ElementDatum[],
          ) => {
            const item = items[0];
            const content = document.createElement('div');
            content.style.maxWidth = 'min(520px, calc(100vw - 48px))';
            content.style.whiteSpace = 'normal';
            content.style.overflowWrap = 'anywhere';
            if (!item?.data) return content;

            if (!item.data.isJobNode) {
              const details = [
                ['表名', item.data.tableName],
                ['完整名称', item.data.canonicalName],
                ['连接器', item.data.connector],
                ['Catalog', item.data.catalogName],
                ['数据库', item.data.databaseName],
                ['数仓层', item.data.dwLayer ?? '-'],
              ];
              details.forEach(([label, value]) => {
                const detail = document.createElement('div');
                detail.textContent = `${label}：${value || '-'}`;
                detail.style.overflowWrap = 'anywhere';
                content.appendChild(detail);
              });
              return content;
            }

            const title = document.createElement('div');
            title.textContent = String(item.data.jobName ?? '');
            content.appendChild(title);

            const runtime = document.createElement('div');
            runtime.textContent = String(item.data.runtimeLabel ?? '- task · -');
            content.appendChild(runtime);
            return content;
          },
        },
      ],
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
        setSelectedJobs([nodeDatum.data as unknown as SelectedJob]);
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

  // 运行态指标的增量刷新：只更新已存在节点/边的 data，不 setData/render/fitView，
  // 保持用户当前的缩放与拖动位置（P1）。首次建图后紧跟着也会跑一次，把刚拉到的
  // jobMetrics 补上去，不需要在上面的建图 effect 里重复注入一份。
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph || !graphData) return;
    const data = toG6Data(graphData, rootNodeId, impactHighlightIds);
    applyJobMetrics(data, jobMetrics);
    const nodeUpdates = data.nodes
      .filter((node) => node.data.isJobNode && node.data.runningAppId)
      .map((node) => ({
        id: node.id,
        data: {
          runtimeLabel: node.data.runtimeLabel,
          recordsWrittenRate: node.data.recordsWrittenRate,
        },
      }));
    const edgeUpdates = data.edges
      .filter((edge) => !edge.data.isCollapsedLink)
      .map((edge) => ({
        id: edge.id,
        data: { isRunningLink: edge.data.isRunningLink },
      }));
    if (nodeUpdates.length > 0) graph.updateNodeData(nodeUpdates);
    if (edgeUpdates.length > 0) graph.updateEdgeData(edgeUpdates);
    void graph.draw();
  }, [jobMetrics, graphData, rootNodeId, impactHighlightIds]);

  // graphHeight 由布局测量得出（非窗口 resize 事件触发），G6 的 autoResize 只监听
  // window.onresize，容器高度自身变化（如 Alert 数量变化）需要手动 resize 一次。
  useEffect(() => {
    graphRef.current?.resize();
  }, [graphHeight]);

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
          <div ref={containerRef} style={{ height: graphHeight }} />
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
