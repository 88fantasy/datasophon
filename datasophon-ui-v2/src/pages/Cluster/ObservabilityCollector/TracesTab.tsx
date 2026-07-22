import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Button, DatePicker, Form, Input, Select, Space, Tag } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useObservabilityStyles } from './observabilityStyles';
import { listTraceServices, listTraces, type TraceRow } from './service';
import TraceDetailDrawer, { formatDuration } from './TraceDetailDrawer';

interface TracesTabProps {
  clusterId: number;
  onShowLogs: (traceId: string) => void;
  serviceName?: string;
  onServiceNameConsumed: () => void;
}

interface TraceFilters {
  timeRange: [Dayjs, Dayjs];
  serviceName?: string;
  status?: string;
  spanName?: string;
  traceId?: string;
}

const { RangePicker } = DatePicker;

function defaultRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(1, 'hour'), dayjs()];
}

function toSeconds(value: Dayjs) {
  return Math.floor(value.valueOf() / 1000);
}

function statusTag(status: string) {
  return status === 'ERROR' ? (
    <Tag color="red">ERROR</Tag>
  ) : (
    <Tag color="green">OK</Tag>
  );
}

const TracesTab: React.FC<TracesTabProps> = ({
  clusterId,
  onShowLogs,
  serviceName,
  onServiceNameConsumed,
}) => {
  const intl = useIntl();
  const t = useCallback(
    (id: string, defaultMessage: string) =>
      intl.formatMessage({ id, defaultMessage }),
    [intl],
  );
  const { styles } = useObservabilityStyles();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm<TraceFilters>();
  const [filters, setFilters] = useState<TraceFilters>({
    timeRange: defaultRange(),
  });
  const filtersRef = useRef(filters);
  const [services, setServices] = useState<string[]>([]);
  const [drawerTraceId, setDrawerTraceId] = useState<string>();

  useEffect(() => {
    const [start, end] = filters.timeRange;
    listTraceServices(clusterId, toSeconds(start), toSeconds(end)).then(
      (result) => {
        setServices(result.data ?? []);
      },
    );
  }, [clusterId, filters.timeRange]);

  useEffect(() => {
    if (!serviceName) return;
    const nextFilters = { ...filters, serviceName };
    form.setFieldsValue(nextFilters);
    filtersRef.current = nextFilters;
    setFilters(nextFilters);
    actionRef.current?.reload();
    onServiceNameConsumed();
  }, [filters, form, onServiceNameConsumed, serviceName]);

  const columns = useMemo<ProColumns<TraceRow>[]>(
    () => [
      {
        title: t('pages.observabilityCollector.startTime', 'Start time'),
        dataIndex: 'timestamp',
        width: 180,
        search: false,
        renderText: (value) =>
          dayjs.utc(value).local().format('MM-DD HH:mm:ss.SSS'),
      },
      {
        title: t('pages.observabilityCollector.service', 'Service'),
        dataIndex: 'serviceName',
        width: 160,
        search: false,
        render: (_, record) => (
          <Tag className={styles.serviceTag}>{record.serviceName}</Tag>
        ),
      },
      {
        title: t('pages.observabilityCollector.rootSpan', 'Root span'),
        dataIndex: 'spanName',
        search: false,
        render: (_, record) => (
          <span className={styles.spanName}>{record.spanName}</span>
        ),
      },
      {
        title: t('pages.observabilityCollector.traceId', 'TraceID'),
        dataIndex: 'traceId',
        width: 220,
        search: false,
        render: (_, record) => (
          <Button
            type="link"
            size="small"
            className={styles.traceId}
            onClick={() => setDrawerTraceId(record.traceId)}
          >
            {record.traceId}
          </Button>
        ),
      },
      {
        title: t('pages.observabilityCollector.spans', 'Spans'),
        dataIndex: 'spanCount',
        width: 90,
        search: false,
        render: (_, record) => <Tag color="blue">{record.spanCount}</Tag>,
      },
      {
        title: t('pages.observabilityCollector.duration', 'Duration'),
        dataIndex: 'duration',
        width: 190,
        search: false,
        render: (_, record) => (
          <span className={styles.durationCell}>
            <span
              className={styles.durationBar}
              style={{
                width: Math.min(Math.max(record.duration / 10_000_000, 8), 120),
                background: record.status === 'ERROR' ? '#ffccc7' : undefined,
              }}
            />
            <span>{formatDuration(record.duration)}</span>
          </span>
        ),
      },
      {
        title: t('pages.observabilityCollector.status', 'Status'),
        dataIndex: 'status',
        width: 100,
        search: false,
        render: (_, record) => statusTag(record.status),
      },
    ],
    [styles, t],
  );

  const applyFilters = (values: TraceFilters) => {
    filtersRef.current = values;
    setFilters(values);
    actionRef.current?.reload();
  };

  const setPreset = (amount: number, unit: 'minute' | 'hour') => {
    const nextRange: [Dayjs, Dayjs] = [dayjs().subtract(amount, unit), dayjs()];
    form.setFieldsValue({ ...filters, timeRange: nextRange });
    applyFilters({ ...filters, timeRange: nextRange });
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
        <Form.Item
          label={t('pages.observabilityCollector.timeRange', 'Time range')}
          name="timeRange"
          style={{ marginBottom: 0 }}
        >
          <RangePicker showTime allowClear={false} />
        </Form.Item>
        <Form.Item
          label={t('pages.observabilityCollector.service', 'Service')}
          name="serviceName"
          style={{ marginBottom: 0 }}
        >
          <Select
            allowClear
            showSearch
            style={{ width: 180 }}
            options={services.map((service) => ({
              label: service,
              value: service,
            }))}
          />
        </Form.Item>
        <Form.Item
          label={t('pages.observabilityCollector.status', 'Status')}
          name="status"
          style={{ marginBottom: 0 }}
        >
          <Select
            allowClear
            style={{ width: 130 }}
            options={[
              { label: 'OK', value: 'OK' },
              { label: 'ERROR', value: 'ERROR' },
            ]}
          />
        </Form.Item>
        <Form.Item
          label={t('pages.observabilityCollector.spanName', 'Span name')}
          name="spanName"
          style={{ marginBottom: 0 }}
        >
          <Input
            placeholder={t(
              'pages.observabilityCollector.spanNameSearchPlaceholder',
              'Search span name',
            )}
            style={{ width: 220 }}
          />
        </Form.Item>
        <Form.Item
          label={t('pages.observabilityCollector.traceId', 'TraceID')}
          name="traceId"
          style={{ marginBottom: 0 }}
        >
          <Input
            placeholder={t(
              'pages.observabilityCollector.traceIdFullPlaceholder',
              'Full TraceID',
            )}
            style={{ width: 240 }}
          />
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
                form.resetFields();
                form.setFieldsValue(nextFilters);
                applyFilters(nextFilters);
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
      <ProTable<TraceRow>
        actionRef={actionRef}
        rowKey="traceId"
        columns={columns}
        search={false}
        options={{ reload: true, density: false, setting: false }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          const currentFilters = filtersRef.current;
          const [start, end] = currentFilters.timeRange;
          const result = await listTraces({
            clusterId,
            start: toSeconds(start),
            end: toSeconds(end),
            serviceName: currentFilters.serviceName,
            status: currentFilters.status,
            spanName: currentFilters.spanName,
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
      <TraceDetailDrawer
        clusterId={clusterId}
        traceId={drawerTraceId}
        open={!!drawerTraceId}
        onClose={() => setDrawerTraceId(undefined)}
        onShowLogs={onShowLogs}
      />
    </div>
  );
};

export default TracesTab;
