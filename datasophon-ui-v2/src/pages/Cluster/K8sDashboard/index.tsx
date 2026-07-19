import { Bar, Line } from '@ant-design/plots';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Segmented,
  Statistic,
  Table,
  Tag,
} from 'antd';
import dayjs from 'dayjs';
import React, { useContext, useEffect, useMemo, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import { getK8sDashboard } from '@/services/k8s';

const HEALTH_COLOR: Record<string, string> = {
  HEALTHY: '#16a34a',
  WARNING: '#d97706',
  CRITICAL: '#dc2626',
};

const HEALTH_TEXT: Record<string, string> = {
  HEALTHY: '健康',
  WARNING: '告警',
  CRITICAL: '异常',
};

const metricPercent = (value?: number) =>
  typeof value === 'number' ? Math.round(value) : 0;

const formatCapacity = (value: number | undefined, unit: string) => {
  if (typeof value !== 'number') return '-';
  if (unit === 'count') return value.toString();
  if (unit === 'core') return `${value.toFixed(1)} Core`;
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB'];
  let formatted = value;
  let index = 0;
  while (formatted >= 1024 && index < units.length - 1) {
    formatted /= 1024;
    index += 1;
  }
  return `${formatted.toFixed(1)} ${units[index]}`;
};

const K8sDashboard: React.FC = () => {
  const context = useContext(ClusterContext);
  if (!context)
    throw new Error('K8sDashboard must be rendered inside ClusterLayout');
  const { clusterId } = context;
  const [range, setRange] = useState('24h');
  const [data, setData] = useState<DATASOPHON.K8sDashboardResponse>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const response = await getK8sDashboard(clusterId, range);
        if (!cancelled) setData(response.data);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    setLoading(true);
    load();
    const timer = window.setInterval(load, 30000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [clusterId, range]);

  const trendData = useMemo(
    () =>
      data?.trends.flatMap((point) => {
        const time = dayjs(point.timestamp).valueOf();
        return [
          ...(typeof point.cpuPercent === 'number'
            ? [{ time, series: 'CPU 使用率', value: point.cpuPercent }]
            : []),
          ...(typeof point.memoryPercent === 'number'
            ? [{ time, series: '内存使用率', value: point.memoryPercent }]
            : []),
          ...(typeof point.networkMbps === 'number'
            ? [{ time, series: '网络吞吐量', value: point.networkMbps }]
            : []),
        ];
      }) ?? [],
    [data?.trends],
  );
  const overview = data?.overview;
  const namespaceChartData = (data?.namespaces ?? []).flatMap((namespace) => [
    {
      name: namespace.name,
      metric: 'CPU（Core）',
      value: namespace.cpuCores ?? 0,
    },
    {
      name: namespace.name,
      metric: '内存（GiB）',
      value: (namespace.memoryBytes ?? 0) / 1024 ** 3,
    },
  ]);

  return (
    <PageContainer
      title={
        <span>
          K8s 集群运行概览
          {data?.observedAt && (
            <span
              style={{
                color: '#8c8c8c',
                fontSize: 14,
                fontWeight: 'normal',
                marginLeft: 12,
              }}
            >
              更新时间：{dayjs(data.observedAt).format('YYYY-MM-DD HH:mm:ss')}
            </span>
          )}
        </span>
      }
      extra={
        <Segmented
          value={range}
          onChange={(value) => setRange(value.toString())}
          options={['1h', '6h', '24h']}
        />
      }
    >
      {data?.telemetry.status === 'UNAVAILABLE' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          title={
            data.telemetry.message ??
            'OTel 指标暂不可用，当前仅展示 K8s API 实时状态。'
          }
        />
      )}

      <Row gutter={[12, 12]}>
        <Col xs={24} sm={12} xl={5}>
          <Card loading={loading} size="small">
            <Statistic
              title="集群状态"
              value={HEALTH_TEXT[overview?.health ?? ''] ?? '-'}
              styles={{
                content: { color: HEALTH_COLOR[overview?.health ?? ''] },
              }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={5}>
          <Card loading={loading} size="small">
            <Statistic
              title="节点 Ready"
              value={`${overview?.readyNodes ?? 0} / ${overview?.totalNodes ?? 0}`}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={5}>
          <Card loading={loading} size="small">
            <Statistic
              title="Pod Running"
              value={`${overview?.runningPods ?? 0} / ${overview?.totalPods ?? 0}`}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={5}>
          <Card loading={loading} size="small">
            <Statistic
              title="Critical"
              value={overview?.critical ?? 0}
              styles={{ content: { color: '#dc2626' } }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={4}>
          <Card loading={loading} size="small">
            <Statistic
              title="Warning"
              value={overview?.warning ?? 0}
              styles={{ content: { color: '#d97706' } }}
            />
          </Card>
        </Col>

        {(data?.capacities ?? []).map((capacity) => (
          <Col key={capacity.name} xs={24} sm={12} lg={6}>
            <Card title={capacity.name} loading={loading} size="small">
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <Progress
                  type="dashboard"
                  percent={metricPercent(capacity.percent)}
                  format={() =>
                    typeof capacity.percent === 'number'
                      ? `${metricPercent(capacity.percent)}%`
                      : '-'
                  }
                  strokeColor="#1677ff"
                />
                <div>
                  <div>
                    已用：{formatCapacity(capacity.used, capacity.unit)}
                  </div>
                  <div style={{ color: '#8c8c8c', marginTop: 4 }}>
                    总计：{formatCapacity(capacity.total, capacity.unit)}
                  </div>
                </div>
              </div>
            </Card>
          </Col>
        ))}

        <Col xs={24} xl={12}>
          <Card
            title="资源趋势"
            loading={loading}
            size="small"
            extra={`时间范围：${range}`}
            style={{ height: 360 }}
          >
            {trendData.length ? (
              <Line
                data={trendData}
                xField="time"
                yField="value"
                seriesField="series"
                height={230}
                smooth
                axis={{
                  x: {
                    labelFormatter: (value: string) =>
                      dayjs(Number(value)).format('HH:mm'),
                  },
                  y: {
                    labelFormatter: (value: string) =>
                      `${Number(value).toFixed(0)}%`,
                  },
                }}
                scale={{ x: { type: 'time' } }}
                tooltip={{
                  title: { field: 'time' },
                  items: [
                    {
                      channel: 'y',
                      valueFormatter: (value: number) =>
                        `${Number(value).toFixed(2)}%`,
                    },
                  ],
                }}
              />
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="等待 OTel 指标聚合数据"
                style={{ height: 230 }}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card
            title="异常与告警"
            loading={loading}
            size="small"
            style={{ height: 360 }}
          >
            <Table
              size="small"
              rowKey={(event) =>
                `${event.object}-${event.reason}-${event.lastTimestamp}`
              }
              pagination={false}
              scroll={{ y: 250 }}
              dataSource={data?.events ?? []}
              locale={{ emptyText: '暂无异常与告警' }}
              columns={[
                {
                  title: '级别',
                  dataIndex: 'type',
                  width: 82,
                  render: (value) => (
                    <Tag color={value === 'Critical' ? 'error' : 'warning'}>
                      {value}
                    </Tag>
                  ),
                },
                { title: '告警', dataIndex: 'reason', width: 130 },
                {
                  title: '对象',
                  dataIndex: 'object',
                  width: 150,
                  ellipsis: true,
                },
                {
                  title: 'Namespace',
                  dataIndex: 'namespace',
                  width: 120,
                  ellipsis: true,
                },
                { title: '详情', dataIndex: 'message', ellipsis: true },
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card
            title="Namespace 资源 Top10"
            loading={loading}
            size="small"
            style={{ height: 520 }}
          >
            {namespaceChartData.length ? (
              <Bar
                data={namespaceChartData}
                xField="name"
                yField="value"
                seriesField="metric"
                height={420}
                axis={{ x: { title: false }, y: { title: false } }}
                legend={{ position: 'top' }}
                tooltip={{
                  items: [
                    {
                      channel: 'y',
                      valueFormatter: (value: number) =>
                        Number(value).toFixed(2),
                    },
                  ],
                }}
              />
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无 Namespace 指标"
              />
            )}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card
            title="工作负载健康"
            loading={loading}
            size="small"
            style={{ height: 520 }}
          >
            <Table
              size="small"
              rowKey={(workload) =>
                `${workload.namespace}-${workload.type}-${workload.name}`
              }
              pagination={false}
              scroll={{ y: 350 }}
              dataSource={data?.workloads ?? []}
              columns={[
                { title: '工作负载', dataIndex: 'name', ellipsis: true },
                { title: '类型', dataIndex: 'type', width: 100 },
                {
                  title: 'Ready',
                  width: 76,
                  render: (_, row) => `${row.ready} / ${row.desired}`,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 82,
                  render: (value) => (
                    <Tag color={value === 'NORMAL' ? 'success' : 'warning'}>
                      {value === 'NORMAL' ? '正常' : '告警'}
                    </Tag>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col span={24}>
          <Card title="最近事件" loading={loading} size="small">
            {data?.events.length ? (
              <Table
                size="small"
                rowKey={(event) =>
                  `${event.object}-${event.reason}-${event.lastTimestamp}`
                }
                pagination={false}
                dataSource={data.events}
                columns={[
                  { title: '类型', dataIndex: 'type', width: 100 },
                  {
                    title: '时间',
                    dataIndex: 'lastTimestamp',
                    width: 180,
                    render: (value) =>
                      value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
                  },
                  { title: '原因', dataIndex: 'reason', width: 160 },
                  { title: '详情', dataIndex: 'message', ellipsis: true },
                  { title: 'Namespace', dataIndex: 'namespace', width: 160 },
                ]}
              />
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无最近事件"
              />
            )}
          </Card>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default K8sDashboard;
