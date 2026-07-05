import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Graph, type IElementEvent } from '@antv/g6';
import { Alert, Button, DatePicker, Empty, Form, Space, Spin } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useRef, useState } from 'react';

import { type TopologyGraph, getTraceTopology } from './service';
import { formatDuration } from './TraceDetailDrawer';
import { useObservabilityStyles } from './observabilityStyles';

interface TopologyTabProps {
  clusterId: number;
  onShowTraces: (serviceName: string) => void;
}

interface TopologyFilters {
  timeRange: [Dayjs, Dayjs];
}

const { RangePicker } = DatePicker;

function defaultRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(1, 'hour'), dayjs()];
}

function toSeconds(value: Dayjs) {
  return Math.floor(value.valueOf() / 1000);
}

export function toGraphData(topology: TopologyGraph) {
  const nodes = topology.nodes.map((node) => {
    const errorRate = node.spanCount > 0 ? node.errorCount / node.spanCount : 0;
    const metrics = [
      `p99 ${formatDuration(node.p99DurationNs)}`,
      `avg ${formatDuration(node.avgDurationNs)}`,
    ];
    if (errorRate > 0) {
      metrics.push(`err ${(errorRate * 100).toFixed(1)}%`);
    }
    return {
      id: node.serviceName,
      data: {
        errorRate,
        metricsText: metrics.join(' · '),
      },
    };
  });
  const edges = topology.edges.map((edge) => {
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
  const { styles } = useObservabilityStyles();
  const [form] = Form.useForm<TopologyFilters>();
  const [filters, setFilters] = useState<TopologyFilters>({
    timeRange: defaultRange(),
  });
  const [topology, setTopology] = useState<TopologyGraph>();
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph>(undefined);
  const onShowTracesRef = useRef(onShowTraces);
  onShowTracesRef.current = onShowTraces;

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
    const data = toGraphData(topology);
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
        type: 'rect',
        style: {
          size: [230, 56],
          radius: 8,
          fill: '#ffffff',
          lineWidth: 1.5,
          stroke: (d) =>
            ((d.data?.errorRate as number) ?? 0) > 0 ? '#ff4d4f' : '#5b8ff9',
          labelText: (d) => `${d.id}\n${d.data?.metricsText ?? ''}`,
          labelPlacement: 'center',
          labelFontSize: 12,
          labelLineHeight: 18,
          cursor: 'pointer',
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
    });
    graph.on('node:click', (event: IElementEvent) => {
      const id = event.target?.id;
      if (typeof id === 'string') {
        onShowTracesRef.current(id);
      }
    });
    graph.render();
    graphRef.current = graph;
  }, [topology]);

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
        <Form.Item label="Time range" name="timeRange" style={{ marginBottom: 0 }}>
          <RangePicker showTime allowClear={false} />
        </Form.Item>
        <Form.Item style={{ marginBottom: 0 }}>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              Query
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                const nextFilters = { timeRange: defaultRange() };
                form.setFieldsValue(nextFilters);
                setFilters(nextFilters);
              }}
            >
              Reset
            </Button>
          </Space>
        </Form.Item>
      </Form>
      <div className={styles.quickBar}>
        <span style={{ color: '#8c8c8c', fontSize: 12 }}>Quick:</span>
        <Button size="small" onClick={() => setPreset(15, 'minute')}>
          Last 15m
        </Button>
        <Button size="small" type="primary" ghost onClick={() => setPreset(1, 'hour')}>
          Last 1h
        </Button>
        <Button size="small" onClick={() => setPreset(6, 'hour')}>
          Last 6h
        </Button>
        <Button size="small" onClick={() => setPreset(24, 'hour')}>
          Last 24h
        </Button>
      </div>
      <Spin spinning={loading}>
        {topology && !hasNodes ? (
          <Empty
            style={{ padding: '80px 0' }}
            description="No topology data in this time range. Ensure the Doris job otel_traces_graph_job is running (data is aggregated every 10 minutes)."
          />
        ) : (
          <>
            {topology && hasNodes && topology.edges.length === 0 && (
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 12 }}
                message="No cross-service calls found in this time range. If edges are expected, check the Doris job otel_traces_graph_job."
              />
            )}
            <div ref={containerRef} style={{ height: 560 }} />
          </>
        )}
      </Spin>
    </div>
  );
};

export default TopologyTab;
