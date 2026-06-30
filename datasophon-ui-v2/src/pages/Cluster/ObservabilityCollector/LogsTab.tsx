import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, DatePicker, Form, Input, Select, Space, Tag } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';

import { type LogRow, listLogs, listTraceServices } from './service';
import { useObservabilityStyles } from './observabilityStyles';

interface LogsTabProps {
  clusterId: number;
  traceId?: string;
  onTraceIdConsumed: () => void;
}

interface LogFilters {
  timeRange: [Dayjs, Dayjs];
  serviceName?: string;
  severities?: string[];
  bodyKeyword?: string;
  traceId?: string;
}

const { RangePicker } = DatePicker;
const severityOptions = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'];

function defaultRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(1, 'hour'), dayjs()];
}

function toSeconds(value: Dayjs) {
  return Math.floor(value.valueOf() / 1000);
}

function severityColor(severity: string) {
  if (severity === 'ERROR') return 'red';
  if (severity === 'WARN') return 'gold';
  if (severity === 'DEBUG') return 'green';
  if (severity === 'FATAL') return 'purple';
  if (severity === 'TRACE') return 'default';
  return 'blue';
}

function logDetail(record: LogRow) {
  return {
    timestamp: record.timestamp,
    severity: record.severityText,
    service_name: record.serviceName,
    trace_id: record.traceId,
    span_id: record.spanId,
    body: record.body,
    log_attributes: record.logAttributes,
    resource_attributes: record.resourceAttributes,
  };
}

const LogsTab: React.FC<LogsTabProps> = ({
  clusterId,
  traceId,
  onTraceIdConsumed,
}) => {
  const { styles } = useObservabilityStyles();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm<LogFilters>();
  const [filters, setFilters] = useState<LogFilters>({
    timeRange: defaultRange(),
    severities: ['INFO', 'WARN', 'ERROR'],
  });
  const filtersRef = useRef(filters);
  const [services, setServices] = useState<string[]>([]);

  useEffect(() => {
    const [start, end] = filters.timeRange;
    listTraceServices(clusterId, toSeconds(start), toSeconds(end)).then((result) => {
      setServices(result.data ?? []);
    });
  }, [clusterId, filters.timeRange]);

  useEffect(() => {
    if (!traceId) return;
    const nextFilters = { ...filters, traceId };
    form.setFieldsValue(nextFilters);
    filtersRef.current = nextFilters;
    setFilters(nextFilters);
    actionRef.current?.reload();
    onTraceIdConsumed();
  }, [filters, form, onTraceIdConsumed, traceId]);

  const columns = useMemo<ProColumns<LogRow>[]>(
    () => [
      {
        title: 'Timestamp',
        dataIndex: 'timestamp',
        width: 180,
        search: false,
        renderText: (value) => dayjs.utc(value).local().format('HH:mm:ss.SSS'),
      },
      {
        title: 'Severity',
        dataIndex: 'severityText',
        width: 110,
        search: false,
        render: (_, record) => (
          <Tag color={severityColor(record.severityText)}>
            {record.severityText || '-'}
          </Tag>
        ),
      },
      {
        title: 'Service',
        dataIndex: 'serviceName',
        width: 160,
        search: false,
        render: (_, record) => (
          <Tag className={styles.serviceTag}>{record.serviceName}</Tag>
        ),
      },
      {
        title: 'Body',
        dataIndex: 'body',
        search: false,
        render: (_, record) => (
          <span style={{ color: record.severityText === 'ERROR' ? '#cf1322' : undefined }}>
            {record.body}
          </span>
        ),
      },
      {
        title: 'TraceID',
        dataIndex: 'traceId',
        width: 220,
        search: false,
        render: (_, record) =>
          record.traceId ? (
            <Button
              type="link"
              size="small"
              className={styles.traceId}
              onClick={() => {
                const nextFilters = { ...filters, traceId: record.traceId };
                form.setFieldsValue(nextFilters);
                filtersRef.current = nextFilters;
                setFilters(nextFilters);
                actionRef.current?.reload();
              }}
            >
              {record.traceId}
            </Button>
          ) : (
            '-'
          ),
      },
    ],
    [filters, form, styles],
  );

  const applyFilters = (values: LogFilters) => {
    filtersRef.current = values;
    setFilters(values);
    actionRef.current?.reload();
  };

  const toggleSeverity = (severity: string) => {
    const current = new Set(filters.severities ?? []);
    if (current.has(severity)) {
      current.delete(severity);
    } else {
      current.add(severity);
    }
    const nextFilters = { ...filters, severities: [...current] };
    form.setFieldsValue(nextFilters);
    applyFilters(nextFilters);
  };

  return (
    <div className={styles.panel}>
      <Form
        form={form}
        layout="vertical"
        initialValues={filters}
        onFinish={applyFilters}
        className={styles.filterBar}
      >
        <Form.Item label="Time range" name="timeRange" style={{ marginBottom: 0 }}>
          <RangePicker showTime allowClear={false} />
        </Form.Item>
        <Form.Item label="Service" name="serviceName" style={{ marginBottom: 0 }}>
          <Select
            allowClear
            showSearch
            style={{ width: 180 }}
            options={services.map((service) => ({ label: service, value: service }))}
          />
        </Form.Item>
        <Form.Item label="Severity" name="severities" style={{ marginBottom: 0 }}>
          <Select
            allowClear
            mode="multiple"
            style={{ width: 220 }}
            options={severityOptions.map((severity) => ({
              label: severity,
              value: severity,
            }))}
          />
        </Form.Item>
        <Form.Item label="Body search" name="bodyKeyword" style={{ marginBottom: 0 }}>
          <Input placeholder="Search body keyword" style={{ width: 260 }} />
        </Form.Item>
        <Form.Item label="TraceID" name="traceId" style={{ marginBottom: 0 }}>
          <Input placeholder="TraceID" style={{ width: 240 }} />
        </Form.Item>
        <Form.Item style={{ marginBottom: 0 }}>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              Query
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                const nextFilters = {
                  timeRange: defaultRange(),
                  severities: ['INFO', 'WARN', 'ERROR'],
                };
                form.resetFields();
                form.setFieldsValue(nextFilters);
                applyFilters(nextFilters);
              }}
            >
              Reset
            </Button>
          </Space>
        </Form.Item>
      </Form>
      <div className={styles.quickBar}>
        <span style={{ color: '#8c8c8c', fontSize: 12 }}>Severity:</span>
        {severityOptions.map((severity) => {
          const active = filters.severities?.includes(severity);
          return (
            <Tag
              key={severity}
              color={active ? severityColor(severity) : undefined}
              style={{ cursor: 'pointer' }}
              onClick={() => toggleSeverity(severity)}
            >
              {severity}
            </Tag>
          );
        })}
      </div>
      <ProTable<LogRow>
        actionRef={actionRef}
        rowKey={(record) => `${record.timestamp}-${record.traceId}-${record.spanId}`}
        columns={columns}
        search={false}
        options={{ reload: true, density: false, setting: false }}
        pagination={{ defaultPageSize: 50, showSizeChanger: true }}
        expandable={{
          expandedRowRender: (record) => (
            <pre className={styles.logDetail}>
              {JSON.stringify(logDetail(record), null, 2)}
            </pre>
          ),
          rowExpandable: () => true,
        }}
        request={async (params) => {
          const currentFilters = filtersRef.current;
          const [start, end] = currentFilters.timeRange;
          const result = await listLogs({
            clusterId,
            start: toSeconds(start),
            end: toSeconds(end),
            serviceName: currentFilters.serviceName,
            severities: currentFilters.severities,
            bodyKeyword: currentFilters.bodyKeyword,
            traceId: currentFilters.traceId,
            page: params.current,
            pageSize: params.pageSize,
          });
          return {
            data: result.data ?? [],
            success: result.code === 200,
            total: result.total ?? 0,
          };
        }}
      />
    </div>
  );
};

export default LogsTab;
