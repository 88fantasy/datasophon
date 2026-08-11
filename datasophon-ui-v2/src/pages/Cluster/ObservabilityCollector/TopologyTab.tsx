import { DownloadOutlined } from '@ant-design/icons';
import {
  type ElementDatum,
  type Fullscreen,
  Graph,
  type IElementEvent,
} from '@antv/g6';
import { useIntl } from '@umijs/max';
import {
  Alert,
  Button,
  Checkbox,
  Empty,
  Input,
  message,
  Spin,
  Tag,
} from 'antd';
import dayjs from 'dayjs';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useFillViewportHeight } from '../_shared/useFillViewportHeight';
import OverviewStats from './OverviewStats';
import { useObservabilityStyles } from './observabilityStyles';
import type {
  ObservabilityTabContext,
  ObservabilityTimeRange,
} from './observabilityTypes';
import ServiceDetailDrawer from './ServiceDetailDrawer';
import { getTraceTopology, type TopologyGraph } from './service';
import { serviceIconFor } from './serviceIcon';
import { formatDuration } from './traceVisual';

interface TopologyTabProps extends ObservabilityTabContext {
  clusterId: number;
  onShowTraces: (serviceName: string) => void;
}

interface ViewFilters {
  onlyError: boolean;
  slowTop5: boolean;
  showAvg: boolean;
}

function toSeconds(value: ObservabilityTimeRange[number]) {
  return Math.floor(value.valueOf() / 1000);
}

interface ToGraphDataOptions {
  showAvg?: boolean;
  onlyError?: boolean;
  slowTop5Ids?: Set<string>;
  highlightId?: string;
}

export function toGraphData(
  topology: TopologyGraph,
  options: ToGraphDataOptions = {},
) {
  const {
    showAvg = true,
    onlyError = false,
    slowTop5Ids,
    highlightId,
  } = options;
  const errorEdgeNodeIds = onlyError
    ? new Set(
        topology.edges
          .filter((edge) => edge.errorCount > 0)
          .flatMap((edge) => [edge.caller, edge.callee]),
      )
    : undefined;
  const filteredNodes = onlyError
    ? topology.nodes.filter(
        (node) =>
          node.errorCount > 0 || errorEdgeNodeIds?.has(node.serviceName),
      )
    : topology.nodes;
  const nodeIds = new Set(filteredNodes.map((node) => node.serviceName));
  const filteredEdges = onlyError
    ? topology.edges.filter(
        (edge) =>
          edge.errorCount > 0 &&
          nodeIds.has(edge.caller) &&
          nodeIds.has(edge.callee),
      )
    : topology.edges;

  const nodes = filteredNodes.map((node) => {
    const errorRate = node.spanCount > 0 ? node.errorCount / node.spanCount : 0;
    const metrics = [`p99 ${formatDuration(node.p99DurationNs)}`];
    if (showAvg) {
      metrics.push(`avg ${formatDuration(node.avgDurationNs)}`);
    }
    if (errorRate > 0) {
      metrics.push(`err ${(errorRate * 100).toFixed(1)}%`);
    }
    // 外部依赖节点(合成 id 形如 "mysql@127.0.0.1:3306")优先按后端反查出的 serviceType 取图标/展示名
    // (如 9030 端口的 dbSystem 是 mysql,但 serviceType 精确反查为 doris),反查不到时回退 dbSystem。
    // 展示名拆成 "doris" + 端点两行，比原始合成 id 更易读。
    const externalLabel = node.serviceType ?? node.dbSystem ?? '';
    const icon = serviceIconFor(
      node.external ? externalLabel : node.serviceName,
    );
    const displayName = node.external
      ? `${externalLabel}\n${node.serviceName.split('@')[1] ?? node.serviceName}`
      : node.serviceName;
    // dbSystem 是 db.system → rpc.system（如 grpc）→ http → other 四级兜底后的展示用标签，不能直接拿它
    // 反推"是不是数据库"——排除 http/other/grpc 会把非 grpc 的 rpc.system（如 thrift/dubbo）误判成数据库。
    // isDatabase 由后端在合并外部依赖时按是否真的落到 db.system 显式标注，DB 徽标改用它判定。
    const isDb = Boolean(node.external) && Boolean(node.isDatabase);
    const isGrpc = Boolean(node.external) && node.dbSystem === 'grpc';
    return {
      id: node.serviceName,
      data: {
        errorRate,
        metricsText: metrics.join(' · '),
        dimmed: Boolean(slowTop5Ids) && !slowTop5Ids?.has(node.serviceName),
        highlighted: node.serviceName === highlightId,
        iconSrc: icon.src,
        iconWidth: icon.width,
        iconHeight: icon.height,
        external: node.external ?? false,
        isDb,
        isGrpc,
        displayName,
      },
    };
  });
  const edges = filteredEdges.map((edge) => {
    const errorRate = edge.callCount > 0 ? edge.errorCount / edge.callCount : 0;
    return {
      id: `${edge.caller}->${edge.callee}`,
      source: edge.caller,
      target: edge.callee,
      data: {
        errorCount: edge.errorCount,
        labelText:
          edge.errorCount > 0
            ? `${edge.callCount} · ${(errorRate * 100).toFixed(1)}% err`
            : `${edge.callCount}`,
      },
    };
  });
  return { nodes, edges };
}

export function summarizeTopology(topology?: TopologyGraph) {
  if (!topology) {
    return {
      serviceCount: 0,
      callCount: 0,
      errorCount: 0,
      maxP99DurationNs: 0,
    };
  }
  return {
    serviceCount: topology.nodes.length,
    callCount: topology.edges.reduce((sum, edge) => sum + edge.callCount, 0),
    errorCount: topology.edges.reduce((sum, edge) => sum + edge.errorCount, 0),
    maxP99DurationNs: Math.max(
      0,
      ...topology.nodes.map((node) => node.p99DurationNs),
    ),
  };
}

const TopologyTab: React.FC<TopologyTabProps> = ({
  clusterId,
  onShowTraces,
  timeRange,
  refreshKey,
}) => {
  const intl = useIntl();
  const t = useCallback(
    (id: string, defaultMessage: string, values?: Record<string, string>) =>
      intl.formatMessage({ id, defaultMessage }, values),
    [intl],
  );
  const { styles } = useObservabilityStyles();
  const [topology, setTopology] = useState<TopologyGraph>();
  const [loading, setLoading] = useState(false);
  const [renderFailed, setRenderFailed] = useState(false);
  const [viewFilters, setViewFilters] = useState<ViewFilters>({
    onlyError: false,
    slowTop5: false,
    showAvg: true,
  });
  const [highlightId, setHighlightId] = useState<string>();
  const [selectedService, setSelectedService] = useState<string>();
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph>(undefined);
  const graphHeight = useFillViewportHeight(containerRef, [topology, renderFailed], {
    onHeightChange: () => graphRef.current?.resize(),
  });

  const slowNodes = useMemo(
    () =>
      [...(topology?.nodes ?? [])]
        .sort((a, b) => b.p99DurationNs - a.p99DurationNs)
        .slice(0, 5),
    [topology],
  );
  const slowTop5Ids = useMemo(() => {
    if (!viewFilters.slowTop5 || !topology) return undefined;
    return new Set(slowNodes.map((node) => node.serviceName));
  }, [slowNodes, topology, viewFilters.slowTop5]);
  const summary = useMemo(() => summarizeTopology(topology), [topology]);

  useEffect(() => {
    if (!clusterId) return;
    const [start, end] = timeRange;
    setLoading(true);
    getTraceTopology(clusterId, toSeconds(start), toSeconds(end))
      .then((result) => {
        setTopology(result.data ?? { nodes: [], edges: [] });
      })
      .finally(() => {
        setLoading(false);
      });
  }, [clusterId, refreshKey, timeRange]);

  useEffect(() => {
    if (!topology || topology.nodes.length === 0) {
      graphRef.current?.destroy();
      graphRef.current = undefined;
      return;
    }
    const data = toGraphData(topology, {
      showAvg: viewFilters.showAvg,
      onlyError: viewFilters.onlyError,
      slowTop5Ids,
      highlightId,
    });
    const renderGraph = (graph: Graph) => {
      setRenderFailed(false);
      void Promise.resolve(graph.render())
        .then(async () => {
          await graph.fitView();
          if (graph.getZoom() < 0.68) {
            await graph.zoomTo(0.68);
            const focusId =
              topology.nodes.find((node) => !node.external)?.serviceName ??
              topology.nodes[0]?.serviceName;
            if (focusId) {
              await graph.focusElement(focusId);
            }
          }
        })
        .catch(() => setRenderFailed(true));
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
        type: 'rect',
        style: {
          size: [208, 68],
          radius: 12,
          fill: (d) =>
            ((d.data?.errorRate as number) ?? 0) > 0
              ? '#fff2f0'
              : d.data?.external
                ? '#f9f0ff'
                : '#f0f5ff',
          stroke: (d) =>
            d.data?.highlighted
              ? '#faad14'
              : ((d.data?.errorRate as number) ?? 0) > 0
                ? '#ff7875'
                : d.data?.external
                  ? '#b37feb'
                  : '#85a5ff',
          lineWidth: (d) => (d.data?.highlighted ? 3 : 1.5),
          opacity: (d) => (d.data?.dimmed ? 0.35 : 1),
          cursor: 'pointer',
          iconSrc: (d) => String(d.data?.iconSrc ?? ''),
          iconWidth: (d) => Number(d.data?.iconWidth ?? 18),
          iconHeight: (d) => Number(d.data?.iconHeight ?? 18),
          iconX: -78,
          iconY: 0,
          labelText: (d) => {
            const name = (d.data?.displayName as string) ?? String(d.id);
            const metrics = String(d.data?.metricsText ?? '');
            return `${name}\n${metrics.split(' · ')[0] ?? ''}`;
          },
          labelPlacement: 'center',
          labelOffsetX: 18,
          labelWordWrap: true,
          labelMaxWidth: 144,
          labelFontSize: 12,
          labelFontWeight: 500,
          labelLineHeight: 17,
          labelFill: '#1f1f1f',
          badgeFontSize: 9,
          badgePadding: [1, 4],
          badges: (d) => {
            const list: Array<{
              text: string;
              placement: 'top-right' | 'right-bottom' | 'left-top';
              backgroundFill: string;
              fill: string;
            }> = [];
            if (d.data?.isDb) {
              list.push({
                text: 'DB',
                placement: 'left-top',
                backgroundFill: '#597ef7',
                fill: '#ffffff',
              });
            }
            if (d.data?.isGrpc) {
              list.push({
                text: 'GRPC',
                placement: 'left-top',
                backgroundFill: '#13c2c2',
                fill: '#ffffff',
              });
            }
            if (d.data?.highlighted) {
              list.push({
                text: '★',
                placement: 'top-right',
                backgroundFill: '#faad14',
                fill: '#ffffff',
              });
            }
            if (((d.data?.errorRate as number) ?? 0) > 0) {
              list.push({
                text: '!',
                placement: 'right-bottom',
                backgroundFill: '#ff4d4f',
                fill: '#ffffff',
              });
            }
            return list;
          },
        },
      },
      edge: {
        type: 'cubic-horizontal',
        style: {
          endArrow: true,
          lineWidth: 1.5,
          stroke: (d) =>
            ((d.data?.errorCount as number) ?? 0) > 0 ? '#ff4d4f' : '#99add1',
          labelText: (d) => (d.data?.labelText as string) ?? '',
          labelFontSize: 10,
          labelBackground: true,
          labelBackgroundFill: '#ffffff',
          labelBackgroundOpacity: 0.75,
        },
      },
      layout: {
        type: 'antv-dagre',
        rankdir: 'LR',
        nodesep: 16,
        ranksep: 64,
      },
      behaviors: [
        'drag-canvas',
        'zoom-canvas',
        'drag-element',
        { type: 'hover-activate', degree: 1 },
      ],
      plugins: [
        { type: 'fullscreen' },
        {
          type: 'tooltip',
          trigger: 'hover',
          getContent: async (_event: IElementEvent, items: ElementDatum[]) => {
            const item = items[0];
            const data = item && 'data' in item ? item.data : undefined;
            if (!data || !('displayName' in data)) {
              return '';
            }
            const container = document.createElement('div');
            container.style.fontSize = '12px';
            container.style.lineHeight = '1.6';
            const name = document.createElement('div');
            name.style.fontWeight = '600';
            const rawName =
              (data as { displayName?: string }).displayName ??
              String(item?.id ?? '');
            name.textContent = rawName.replace(/\n/g, ' ');
            const metrics = document.createElement('div');
            metrics.textContent = String(
              (data as { metricsText?: string }).metricsText ?? '',
            );
            container.append(name, metrics);
            return container;
          },
        },
        {
          type: 'toolbar',
          position: 'top-right',
          getItems: () => [
            {
              id: 'zoom-in',
              value: 'zoom-in',
              title: t(
                'pages.observabilityCollector.topologyZoomIn',
                'Zoom in',
              ),
            },
            {
              id: 'zoom-out',
              value: 'zoom-out',
              title: t(
                'pages.observabilityCollector.topologyZoomOut',
                'Zoom out',
              ),
            },
            {
              id: 'auto-fit',
              value: 'auto-fit',
              title: t(
                'pages.observabilityCollector.topologyAutoFit',
                'Fit view',
              ),
            },
            {
              id: 'request-fullscreen',
              value: 'fullscreen',
              title: t(
                'pages.observabilityCollector.topologyFullscreen',
                'Fullscreen',
              ),
            },
          ],
          onClick: (value: string) => {
            const g = graphRef.current;
            if (!g) return;
            switch (value) {
              case 'zoom-in':
                g.zoomBy(1.2);
                break;
              case 'zoom-out':
                g.zoomBy(1 / 1.2);
                break;
              case 'auto-fit':
                g.fitView();
                break;
              case 'fullscreen':
                g.getPluginInstance<Fullscreen>('fullscreen').request();
                break;
              default:
                break;
            }
          },
        },
      ],
    });
    graph.on('node:click', (event: IElementEvent) => {
      const id = event.target?.id;
      if (typeof id !== 'string') return;
      // 外部依赖节点(mysql@host:port 等合成 id)没有 service_name 概况可查,不弹详情面板。
      const nodeDatum = graph.getNodeData(id);
      if (nodeDatum?.data?.external) return;
      setSelectedService(id);
    });
    graphRef.current = graph;
    renderGraph(graph);
  }, [topology, viewFilters, slowTop5Ids, highlightId, t]);

  useEffect(() => {
    return () => {
      graphRef.current?.destroy();
      graphRef.current = undefined;
    };
  }, []);

  const handleSearch = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) {
      setHighlightId(undefined);
      return;
    }
    const match = topology?.nodes.find((node) =>
      node.serviceName.toLowerCase().includes(trimmed.toLowerCase()),
    );
    if (!match) {
      message.warning(
        t(
          'pages.observabilityCollector.topologyServiceNotFound',
          'Service "{name}" not found',
          {
            name: trimmed,
          },
        ),
      );
      return;
    }
    setHighlightId(match.serviceName);
    graphRef.current?.focusElement(match.serviceName);
  };

  const handleExport = async () => {
    const graph = graphRef.current;
    if (!graph) {
      message.warning(
        t(
          'pages.observabilityCollector.topologyNotLoaded',
          'Topology graph not loaded yet',
        ),
      );
      return;
    }
    const dataURL = await graph.toDataURL({
      type: 'image/png',
      encoderOptions: 1,
    });
    const link = document.createElement('a');
    link.href = dataURL;
    link.download = `topology-${dayjs().format('YYYYMMDDHHmmss')}.png`;
    link.click();
  };

  const hasNodes = (topology?.nodes.length ?? 0) > 0;

  return (
    <div className={styles.panel}>
      <OverviewStats
        items={[
          {
            title: t(
              'pages.observabilityCollector.topologyServiceCount',
              '服务与依赖',
            ),
            value: summary.serviceCount,
            hint: t(
              'pages.observabilityCollector.topologyServiceCountHint',
              '当前时间窗口内参与调用的节点',
            ),
          },
          {
            title: t(
              'pages.observabilityCollector.topologyCallCount',
              '跨服务调用',
            ),
            value: summary.callCount,
            hint: `${topology?.edges.length ?? 0} ${t(
              'pages.observabilityCollector.topologyRelations',
              '条调用关系',
            )}`,
          },
          {
            title: t(
              'pages.observabilityCollector.topologyErrorCount',
              '异常调用',
            ),
            value: summary.errorCount,
            hint:
              summary.callCount > 0
                ? `${((summary.errorCount / summary.callCount) * 100).toFixed(
                    2,
                  )}%`
                : '0%',
            tone: summary.errorCount > 0 ? 'danger' : 'success',
          },
          {
            title: 'P99 Max',
            value: formatDuration(summary.maxP99DurationNs),
            hint: slowNodes[0]?.serviceName ?? '-',
            tone: 'warning',
          },
        ]}
      />
      <div className={styles.quickBar}>
        <Checkbox
          checked={viewFilters.onlyError}
          onChange={(e) =>
            setViewFilters((prev) => ({ ...prev, onlyError: e.target.checked }))
          }
        >
          {t('pages.observabilityCollector.topologyOnlyError', 'Only errors')}
        </Checkbox>
        <Checkbox
          checked={viewFilters.slowTop5}
          onChange={(e) =>
            setViewFilters((prev) => ({ ...prev, slowTop5: e.target.checked }))
          }
        >
          {t('pages.observabilityCollector.topologySlowTop5', 'Slow top 5')}
        </Checkbox>
        <Checkbox
          checked={viewFilters.showAvg}
          onChange={(e) =>
            setViewFilters((prev) => ({ ...prev, showAvg: e.target.checked }))
          }
        >
          {t(
            'pages.observabilityCollector.topologyShowAvg',
            'Show avg duration',
          )}
        </Checkbox>
        <Input.Search
          placeholder={t(
            'pages.observabilityCollector.topologySearchPlaceholder',
            'Search service name',
          )}
          allowClear
          style={{ width: 200 }}
          onSearch={handleSearch}
        />
        <Button size="small" icon={<DownloadOutlined />} onClick={handleExport}>
          {t('pages.observabilityCollector.topologyExport', 'Export')}
        </Button>
      </div>
      <Spin spinning={loading}>
        {topology && !hasNodes ? (
          <Empty
            style={{ padding: '80px 0' }}
            description={t(
              'pages.observabilityCollector.topologyEmptyDescription',
              'No topology data in this time range. Ensure the Doris job otel_traces_graph_job is running (data is aggregated every 10 minutes).',
            )}
          />
        ) : (
          <div className={styles.topologyWorkspace}>
            <div className={styles.topologyCanvas}>
              {topology && hasNodes && topology.edges.length === 0 && (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  title={t(
                    'pages.observabilityCollector.topologyNoEdgesAlert',
                    'No cross-service calls found in this time range. If edges are expected, check the Doris job otel_traces_graph_job.',
                  )}
                />
              )}
              {renderFailed && (
                <Alert
                  type="error"
                  showIcon
                  title={t(
                    'pages.observabilityCollector.topologyRenderFailed',
                    '拓扑图渲染失败，请刷新后重试。',
                  )}
                />
              )}
              <div ref={containerRef} style={{ height: graphHeight }} />
            </div>
            <aside className={styles.insightPanel}>
              <div className={styles.insightTitle}>
                {t(
                  'pages.observabilityCollector.slowServiceInsight',
                  '慢服务 Top 5',
                )}
              </div>
              {slowNodes.map((node, index) => (
                <div className={styles.insightItem} key={node.serviceName}>
                  <span className={styles.insightRank}>{index + 1}</span>
                  <span
                    className={styles.insightService}
                    title={node.serviceName}
                  >
                    {node.serviceName}
                  </span>
                  <Tag color={index === 0 ? 'orange' : 'blue'}>
                    {formatDuration(node.p99DurationNs)}
                  </Tag>
                </div>
              ))}
            </aside>
          </div>
        )}
      </Spin>
      <ServiceDetailDrawer
        clusterId={clusterId}
        serviceName={selectedService}
        open={Boolean(selectedService)}
        timeRange={timeRange}
        onClose={() => setSelectedService(undefined)}
        onShowTraces={(serviceName) => {
          onShowTraces(serviceName);
          setSelectedService(undefined);
        }}
      />
    </div>
  );
};

export default TopologyTab;
