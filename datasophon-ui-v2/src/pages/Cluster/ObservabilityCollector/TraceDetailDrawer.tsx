import {
  ApartmentOutlined,
  DatabaseOutlined,
  FieldTimeOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { Drawer, Empty, Segmented, Spin, Statistic, Tag } from 'antd';
import clsx from 'clsx';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';

import { type SpanNode, getTraceDetail } from './service';
import { useObservabilityStyles } from './observabilityStyles';

interface TraceDetailDrawerProps {
  clusterId: number;
  traceId?: string;
  open: boolean;
  onClose: () => void;
  onShowLogs: (traceId: string) => void;
}

interface SpanTreeNode extends SpanNode {
  children: SpanTreeNode[];
  depth: number;
}

function parseTime(value: string) {
  const parsed = dayjs.utc(value);
  return parsed.isValid() ? parsed.valueOf() : 0;
}

export function formatDuration(durationNs: number) {
  if (!durationNs) return '-';
  const ms = durationNs / 1_000_000;
  if (ms < 1) return `${(durationNs / 1_000).toFixed(1)} us`;
  if (ms < 1000) return `${ms.toFixed(ms < 10 ? 2 : 1)} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

function statusIsError(statusCode?: string) {
  return statusCode === 'STATUS_CODE_ERROR' || statusCode === 'ERROR';
}

function buildTree(spans: SpanNode[]) {
  const byId = new Map<string, SpanTreeNode>();
  const roots: SpanTreeNode[] = [];
  for (const span of spans) {
    byId.set(span.spanId, { ...span, children: [], depth: 0 });
  }
  for (const node of byId.values()) {
    const parent = node.parentSpanId ? byId.get(node.parentSpanId) : undefined;
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const flatten: SpanTreeNode[] = [];
  const visit = (node: SpanTreeNode, depth: number) => {
    node.depth = depth;
    flatten.push(node);
    node.children.sort((a, b) => parseTime(a.timestamp) - parseTime(b.timestamp));
    for (const child of node.children) visit(child, depth + 1);
  };
  roots.sort((a, b) => parseTime(a.timestamp) - parseTime(b.timestamp));
  for (const root of roots) visit(root, 0);
  return flatten;
}

function kindColor(kind: string) {
  if (kind.includes('CLIENT')) return '#52c41a';
  if (kind.includes('SERVER')) return '#1677ff';
  if (kind.includes('PRODUCER') || kind.includes('CONSUMER')) return '#722ed1';
  return '#faad14';
}

function renderAttributes(values: Record<string, unknown>) {
  const entries = Object.entries(values ?? {});
  if (entries.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  return entries.map(([key, value]) => (
    <div key={key}>
      <div>{key}</div>
      <div>{typeof value === 'string' ? value : JSON.stringify(value)}</div>
    </div>
  ));
}

const TraceDetailDrawer: React.FC<TraceDetailDrawerProps> = ({
  clusterId,
  traceId,
  open,
  onClose,
  onShowLogs,
}) => {
  const { styles } = useObservabilityStyles();
  const [loading, setLoading] = useState(false);
  const [spans, setSpans] = useState<SpanNode[]>([]);
  const [selectedSpanId, setSelectedSpanId] = useState<string>();
  const [detailTab, setDetailTab] = useState('span');

  useEffect(() => {
    if (!open || !traceId) return;
    let cancelled = false;
    setLoading(true);
    getTraceDetail(clusterId, traceId)
      .then((result) => {
        if (cancelled) return;
        const data = result.data ?? [];
        setSpans(data);
        setSelectedSpanId(data[0]?.spanId);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [clusterId, open, traceId]);

  const flattenedSpans = useMemo(() => buildTree(spans), [spans]);
  const selectedSpan =
    flattenedSpans.find((span) => span.spanId === selectedSpanId) ??
    flattenedSpans[0];
  const rootStart = flattenedSpans.length
    ? Math.min(...flattenedSpans.map((span) => parseTime(span.timestamp)))
    : 0;
  const rootEnd = flattenedSpans.length
    ? Math.max(
        ...flattenedSpans.map((span) =>
          span.endTime
            ? parseTime(span.endTime)
            : parseTime(span.timestamp) + span.duration / 1_000_000,
        ),
      )
    : 0;
  const totalMs = Math.max(rootEnd - rootStart, 1);
  const errorCount = spans.filter((span) => statusIsError(span.statusCode)).length;
  const serviceCount = new Set(spans.map((span) => span.serviceName)).size;

  const detailContent = () => {
    if (!selectedSpan) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    if (detailTab === 'resource') {
      return renderAttributes(selectedSpan.resourceAttributes);
    }
    if (detailTab === 'events') {
      return selectedSpan.events.length ? (
        <pre className={styles.attrValue}>
          {JSON.stringify(selectedSpan.events, null, 2)}
        </pre>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
      );
    }
    if (detailTab === 'logs') {
      return (
        <Tag
          color="blue"
          style={{ cursor: 'pointer' }}
          onClick={() => traceId && onShowLogs(traceId)}
        >
          {traceId}
        </Tag>
      );
    }
    return renderAttributes(selectedSpan.spanAttributes);
  };

  return (
    <Drawer
      title="Trace details"
      size={920}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={traceId ? <Tag>{traceId}</Tag> : undefined}
    >
      <Spin spinning={loading}>
        {flattenedSpans.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
                gap: 16,
                marginBottom: 16,
              }}
            >
              <Statistic
                title="Duration"
                value={formatDuration(Math.round(totalMs * 1_000_000))}
              />
              <Statistic title="Spans" value={spans.length} />
              <Statistic
                title="Error spans"
                value={errorCount}
                styles={{ content: { color: '#cf1322' } }}
              />
              <Statistic title="Services" value={serviceCount} />
            </div>

            <div className={styles.waterfallHeader}>
              <div style={{ width: 340 }}>Service / span</div>
              <div style={{ flex: 1, display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)' }}>
                {[0, 25, 50, 75, 100].map((tick) => (
                  <span key={tick}>{Math.round((totalMs * tick) / 100)}ms</span>
                ))}
              </div>
            </div>
            <div className={styles.waterfallBody}>
              {flattenedSpans.map((span) => {
                const startMs = Math.max(parseTime(span.timestamp) - rootStart, 0);
                const widthMs = Math.max(span.duration / 1_000_000, 1);
                const left = Math.min((startMs / totalMs) * 100, 100);
                const width = Math.max(Math.min((widthMs / totalMs) * 100, 100 - left), 0.5);
                const hasError = statusIsError(span.statusCode);
                return (
                  <div
                    key={span.spanId}
                    className={clsx(
                      styles.spanRow,
                      span.spanId === selectedSpan?.spanId && styles.selectedSpanRow,
                    )}
                    onClick={() => setSelectedSpanId(span.spanId)}
                  >
                    <div className={styles.spanNameCol}>
                      <span style={{ width: span.depth * 16 }} />
                      <span
                        style={{
                          display: 'inline-block',
                          width: 7,
                          height: 7,
                          borderRadius: '50%',
                          marginRight: 6,
                          background: kindColor(span.spanKind),
                        }}
                      />
                      <span
                        className={styles.spanName}
                        style={{ color: hasError ? '#cf1322' : undefined }}
                      >
                        {span.spanName}
                      </span>
                      <span style={{ marginLeft: 'auto', color: '#8c8c8c', fontSize: 11 }}>
                        {formatDuration(span.duration)}
                      </span>
                    </div>
                    <div className={styles.timelineCol}>
                      <span
                        className={styles.spanBar}
                        style={{
                          left: `${left}%`,
                          width: `${width}%`,
                          background: hasError ? '#ff7875' : kindColor(span.spanKind),
                        }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            <div style={{ borderTop: '1px solid #f0f0f0', marginTop: 8 }}>
              <Segmented
                style={{ margin: '12px 24px 0' }}
                value={detailTab}
                onChange={(value) => setDetailTab(String(value))}
                options={[
                  { label: 'Span attributes', value: 'span', icon: <ApartmentOutlined /> },
                  { label: 'Resource', value: 'resource', icon: <DatabaseOutlined /> },
                  { label: `Events (${selectedSpan?.events.length ?? 0})`, value: 'events', icon: <FieldTimeOutlined /> },
                  { label: 'Logs', value: 'logs', icon: <FileTextOutlined /> },
                ]}
              />
              <div className={styles.detailGrid}>
                {detailContent()}
              </div>
            </div>
          </>
        )}
      </Spin>
    </Drawer>
  );
};

export default TraceDetailDrawer;
