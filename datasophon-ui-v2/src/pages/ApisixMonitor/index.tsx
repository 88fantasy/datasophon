import { Card, Col, Row, Typography } from 'antd';
import { type FC, useCallback, useState } from 'react';
import AreaPanel from './panels/AreaPanel';

const BANDWIDTH_COLORS: Record<string, string> = {
  ingress: '#52c41a',
  egress: '#1677ff',
};

const CONNECTION_COLORS: Record<string, string> = {
  active: '#1677ff',
  reading: '#13c2c2',
  writing: '#fa8c16',
  waiting: '#8c8c8c',
};
import StatPanel from './panels/StatPanel';
import StatusStatPanel from './panels/StatusStatPanel';
import TimeSeriesPanel from './panels/TimeSeriesPanel';
import DashboardToolbar from './toolbar/DashboardToolbar';
import type { RefreshInterval, TimeRange } from './toolbar/DashboardToolbar';
import {
  apisixLatencyData,
  bandwidthData,
  connectionStateData,
  etcdIndexData,
  instantValues,
  requestLatencyData,
  rpsByCodeData,
  rpsPerServiceData,
  sharedDictData,
  totalRpsData,
  upstreamLatencyData,
} from './mock/apisixMockData';

const { Title } = Typography;

const MOCK_INSTANCES = ['10.0.0.1:9091', '10.0.0.2:9091'];
const MOCK_SERVICES = ['order-service', 'user-service', 'payment-service'];

// Etcd 阈值：value >= 1 时绿色（Healthy），< 1 时红色
const ETCD_THRESHOLDS = [
  { value: null, color: '#ff4d4f', label: 'Unreachable' },
  { value: 1, color: '#52c41a', label: 'Healthy' },
];

// Nginx 错误阈值：0 绿色，>= 1 黄色
const NGINX_ERR_THRESHOLDS = [
  { value: null, color: '#52c41a', label: 'OK' },
  { value: 1, color: '#faad14', label: 'Warning' },
];

// 延迟分位线颜色
const LATENCY_COLORS: Record<string, string> = {
  p90: '#1677ff',
  p95: '#faad14',
  p99: '#ff4d4f',
};

// 服务颜色
const SERVICE_COLORS: Record<string, string> = {
  'order-service': '#1677ff',
  'user-service': '#52c41a',
  'payment-service': '#722ed1',
};

// 共享字典颜色
const DICT_COLORS: Record<string, string> = {
  'prometheus-metrics': '#ff4d4f',
  'plugin-limit-req': '#1677ff',
  'plugin-limit-conn': '#faad14',
  'balancer-ewma': '#52c41a',
};

const ROW_GUTTER: [number, number] = [16, 16];

const ApisixDashboard: FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1h');
  const [refreshInterval, setRefreshInterval] = useState<RefreshInterval>('30s');
  const [selectedInstances, setSelectedInstances] = useState<string[]>(MOCK_INSTANCES);
  const [selectedServices, setSelectedServices] = useState<string[]>(MOCK_SERVICES);
  // refreshKey 用于触发子组件重渲染（模拟数据刷新）
  const [refreshKey, setRefreshKey] = useState(0);

  const handleRefresh = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  return (
    <div className="p-4" key={refreshKey}>
      <Title level={4} style={{ marginBottom: 16 }}>
        Apache APISIX 监控看板
      </Title>

      {/* 工具栏 */}
      <DashboardToolbar
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
        refreshInterval={refreshInterval}
        onRefreshIntervalChange={setRefreshInterval}
        instances={MOCK_INSTANCES}
        selectedInstances={selectedInstances}
        onInstancesChange={setSelectedInstances}
        services={MOCK_SERVICES}
        selectedServices={selectedServices}
        onServicesChange={setSelectedServices}
        onRefresh={handleRefresh}
      />

      {/* R1 — 摘要统计（col-span 8 each）*/}
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <StatPanel
            title="Total Requests"
            value={instantValues.totalRequests}
            color="#52c41a"
          />
        </Col>
        <Col span={8}>
          <StatPanel
            title="Accepted Connections"
            value={instantValues.acceptedConnections}
            color="#1677ff"
          />
        </Col>
        <Col span={8}>
          <StatPanel
            title="Handled Connections"
            value={instantValues.handledConnections}
            color="#1677ff"
          />
        </Col>
      </Row>

      {/* R2 — 状态指示 */}
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <StatusStatPanel
            title="Etcd Reachable"
            value={instantValues.etcdReachable}
            thresholds={ETCD_THRESHOLDS}
          />
        </Col>
        <Col span={12}>
          <StatusStatPanel
            title="Nginx Metric Errors"
            value={instantValues.nginxMetricErrors}
            thresholds={NGINX_ERR_THRESHOLDS}
          />
        </Col>
      </Row>

      {/* R3 — 流量 */}
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <TimeSeriesPanel
            title="Total Requests per Second"
            data={totalRpsData}
            unit=" req/s"
            colorMap={{ RPS: '#1677ff' }}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="RPS by Status Code"
            data={rpsByCodeData}
            unit=" req/s"
          />
        </Col>
      </Row>

      {/* R4 — 延迟（col-span 8 each）*/}
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <TimeSeriesPanel
            title="Request Latency"
            data={requestLatencyData}
            unit=" ms"
            colorMap={LATENCY_COLORS}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="APISIX Latency"
            data={apisixLatencyData}
            unit=" ms"
            colorMap={LATENCY_COLORS}
          />
        </Col>
        <Col span={8}>
          <TimeSeriesPanel
            title="Upstream Latency"
            data={upstreamLatencyData}
            unit=" ms"
            colorMap={LATENCY_COLORS}
          />
        </Col>
      </Row>

      {/* R5 — 带宽 */}
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <AreaPanel
            title="Total Bandwidth"
            data={bandwidthData}
            stack
            unit="bytes"
            colorMap={BANDWIDTH_COLORS}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="RPS per Service"
            data={rpsPerServiceData}
            unit=" req/s"
            colorMap={SERVICE_COLORS}
          />
        </Col>
      </Row>

      {/* R6 — 连接 & 共享字典 */}
      <Row gutter={ROW_GUTTER} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <AreaPanel
            title="Nginx Connection State"
            data={connectionStateData}
            stack
            unit="short"
            colorMap={CONNECTION_COLORS}
          />
        </Col>
        <Col span={12}>
          <TimeSeriesPanel
            title="Nginx Shared Dict Free Space (%)"
            data={sharedDictData}
            unit="%"
            colorMap={DICT_COLORS}
          />
        </Col>
      </Row>

      {/* R7 — Etcd（全行）*/}
      <Row gutter={ROW_GUTTER}>
        <Col span={24}>
          <TimeSeriesPanel
            title="Etcd Modify Indexes"
            data={etcdIndexData}
            height={200}
          />
        </Col>
      </Row>
    </div>
  );
};

export default ApisixDashboard;
