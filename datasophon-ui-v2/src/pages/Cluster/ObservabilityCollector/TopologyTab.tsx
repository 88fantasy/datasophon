import {
  DownloadOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Circle } from '@antv/g';
import {
  type BaseEdgeStyleProps,
  CubicHorizontal,
  type ElementDatum,
  ExtensionCategory,
  type Fullscreen,
  Graph,
  type IElementEvent,
  type NodeData,
  register,
  subStyleProps,
} from '@antv/g6';
import { useIntl } from '@umijs/max';
import {
  Alert,
  Button,
  Checkbox,
  DatePicker,
  Empty,
  Form,
  Input,
  message,
  Space,
  Spin,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useObservabilityStyles } from './observabilityStyles';
import ServiceDetailDrawer from './ServiceDetailDrawer';
import { getTraceTopology, type TopologyGraph } from './service';
import { serviceIconFor } from './serviceIcon';
import { formatDuration } from './TraceDetailDrawer';

// 边上流动的小圆点动效,参考 G6 官方示例 animation/persistence#fly-marker:
// 圆点的 offsetPath 绑定到边的 key shape(路径),动画 offsetDistance 0→1 使其沿边循环移动。
class FlyMarkerCubic extends CubicHorizontal {
  private getMarkerStyle(attributes: BaseEdgeStyleProps) {
    return {
      r: 3,
      fill: '#5b8ff9',
      offsetPath: this.shapeMap.key,
      ...subStyleProps(attributes, 'marker'),
    };
  }

  onCreate() {
    const marker = this.upsert(
      'marker',
      Circle,
      this.getMarkerStyle(this.attributes),
      this,
    );
    marker?.animate([{ offsetDistance: 0 }, { offsetDistance: 1 }], {
      duration: 2500,
      iterations: Infinity,
    });
  }
}

register(ExtensionCategory.EDGE, 'fly-marker-cubic', FlyMarkerCubic);

interface TopologyTabProps {
  clusterId: number;
  onShowTraces: (serviceName: string) => void;
}

interface TopologyFilters {
  timeRange: [Dayjs, Dayjs];
}

interface ViewFilters {
  onlyError: boolean;
  slowTop5: boolean;
  showAvg: boolean;
}

const { RangePicker } = DatePicker;

function defaultRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(1, 'hour'), dayjs()];
}

function toSeconds(value: Dayjs) {
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

const TopologyTab: React.FC<TopologyTabProps> = ({
  clusterId,
  onShowTraces,
}) => {
  const intl = useIntl();
  const t = useCallback(
    (id: string, defaultMessage: string, values?: Record<string, string>) =>
      intl.formatMessage({ id, defaultMessage }, values),
    [intl],
  );
  const { styles } = useObservabilityStyles();
  const [form] = Form.useForm<TopologyFilters>();
  const [filters, setFilters] = useState<TopologyFilters>({
    timeRange: defaultRange(),
  });
  const [topology, setTopology] = useState<TopologyGraph>();
  const [loading, setLoading] = useState(false);
  const [viewFilters, setViewFilters] = useState<ViewFilters>({
    onlyError: false,
    slowTop5: false,
    showAvg: true,
  });
  const [highlightId, setHighlightId] = useState<string>();
  const [selectedService, setSelectedService] = useState<string>();
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph>(undefined);

  const slowTop5Ids = useMemo(() => {
    if (!viewFilters.slowTop5 || !topology) return undefined;
    return new Set(
      [...topology.nodes]
        .sort((a, b) => b.p99DurationNs - a.p99DurationNs)
        .slice(0, 5)
        .map((node) => node.serviceName),
    );
  }, [topology, viewFilters.slowTop5]);

  useEffect(() => {
    const [start, end] = filters.timeRange;
    setLoading(true);
    getTraceTopology(clusterId, toSeconds(start), toSeconds(end))
      .then((result) => {
        setTopology(result.data ?? { nodes: [], edges: [] });
      })
      .finally(() => {
        setLoading(false);
      });
  }, [clusterId, filters.timeRange]);

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
    if (graphRef.current) {
      graphRef.current.setData(data);
      graphRef.current.render();
      return;
    }
    if (!containerRef.current) return;
    const graph = new Graph({
      container: containerRef.current,
      autoFit: 'view',
      padding: 24,
      data,
      node: {
        type: 'image',
        style: {
          size: (d: NodeData) => [
            ((d.data?.iconWidth as number) ?? 18) * 2,
            ((d.data?.iconHeight as number) ?? 18) * 2,
          ],
          src: (d: NodeData) => (d.data?.iconSrc as string) ?? '',
          opacity: (d) => (d.data?.dimmed ? 0.35 : 1),
          cursor: 'pointer',
          labelText: (d) => (d.data?.displayName as string) ?? String(d.id),
          labelFontSize: 12,
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
        type: 'fly-marker-cubic',
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
            if (!data || !('iconSrc' in data)) {
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
    graph.render();
    graphRef.current = graph;
  }, [topology, viewFilters, slowTop5Ids, highlightId, t]);

  useEffect(() => {
    return () => {
      graphRef.current?.destroy();
      graphRef.current = undefined;
    };
  }, []);

  const setPreset = (amount: number, unit: 'minute' | 'hour') => {
    const nextRange: [Dayjs, Dayjs] = [dayjs().subtract(amount, unit), dayjs()];
    form.setFieldsValue({ timeRange: nextRange });
    setFilters({ timeRange: nextRange });
  };

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
      <Form
        form={form}
        layout="vertical"
        initialValues={filters}
        onFinish={setFilters}
        className={styles.filterBar}
      >
        <Form.Item
          label={t('pages.observabilityCollector.timeRange', 'Time range')}
          name="timeRange"
          style={{ marginBottom: 0 }}
        >
          <RangePicker showTime allowClear={false} />
        </Form.Item>
        <Form.Item style={{ marginBottom: 0 }}>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              {t('pages.observabilityCollector.query', 'Query')}
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                const nextFilters = { timeRange: defaultRange() };
                form.setFieldsValue(nextFilters);
                setFilters(nextFilters);
              }}
            >
              {t('pages.observabilityCollector.reset', 'Reset')}
            </Button>
          </Space>
        </Form.Item>
      </Form>
      <div className={styles.quickBar}>
        <span style={{ color: '#8c8c8c', fontSize: 12 }}>
          {t('pages.observabilityCollector.quick', 'Quick')}:
        </span>
        <Button size="small" onClick={() => setPreset(15, 'minute')}>
          {t('pages.observabilityCollector.presetLast15m', 'Last 15m')}
        </Button>
        <Button
          size="small"
          type="primary"
          ghost
          onClick={() => setPreset(1, 'hour')}
        >
          {t('pages.observabilityCollector.presetLast1h', 'Last 1h')}
        </Button>
        <Button size="small" onClick={() => setPreset(6, 'hour')}>
          {t('pages.observabilityCollector.presetLast6h', 'Last 6h')}
        </Button>
        <Button size="small" onClick={() => setPreset(24, 'hour')}>
          {t('pages.observabilityCollector.presetLast24h', 'Last 24h')}
        </Button>
      </div>
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
          <>
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
            <div ref={containerRef} style={{ height: 560 }} />
          </>
        )}
      </Spin>
      <ServiceDetailDrawer
        clusterId={clusterId}
        serviceName={selectedService}
        open={Boolean(selectedService)}
        timeRange={filters.timeRange}
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
