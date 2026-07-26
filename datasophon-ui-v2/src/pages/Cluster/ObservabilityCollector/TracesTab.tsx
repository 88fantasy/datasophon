import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Button, Form, Input, Select, Space, Tag } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import OverviewStats from './OverviewStats';
import { useObservabilityStyles } from './observabilityStyles';
import type { ObservabilityTabContext } from './observabilityTypes';
import { listTraceServices, listTraces, type TraceRow } from './service';
import TraceDetailDrawer from './TraceDetailDrawer';
import { durationBarWidth, formatDuration } from './traceVisual';

interface TracesTabProps extends ObservabilityTabContext {
  clusterId: number;
  onShowLogs: (traceId: string) => void;
  serviceName?: string;
  onServiceNameConsumed: () => void;
}

interface TraceFilters {
  serviceName?: string;
  status?: string;
  spanName?: string;
  traceId?: string;
}

function toSeconds(value: ObservabilityTabContext['timeRange'][number]) {
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
  timeRange,
  refreshKey,
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
  const [filters, setFilters] = useState<TraceFilters>({});
  const filtersRef = useRef(filters);
  const [services, setServices] = useState<string[]>([]);
  const [drawerTraceId, setDrawerTraceId] = useState<string>();
  const [traceTotal, setTraceTotal] = useState(0);
  const [pageRows, setPageRows] = useState<TraceRow[]>([]);
  const maxPageDuration = Math.max(0, ...pageRows.map((row) => row.duration));

  useEffect(() => {
    if (!clusterId) return;
    const [start, end] = timeRange;
    listTraceServices(clusterId, toSeconds(start), toSeconds(end)).then(
      (result) => {
        setServices(result.data ?? []);
      },
    );
  }, [clusterId, refreshKey, timeRange]);

  useEffect(() => {
    actionRef.current?.reload();
  }, [refreshKey, timeRange]);

  useEffect(() => {
    if (!serviceName) return;
    const nextFilters = { ...filtersRef.current, serviceName };
    form.setFieldsValue(nextFilters);
    filtersRef.current = nextFilters;
    setFilters(nextFilters);
    actionRef.current?.reload();
    onServiceNameConsumed();
  }, [form, onServiceNameConsumed, serviceName]);

  const columns = useMemo<ProColumns<TraceRow>[]>(
    () => [
      {
        title: t('pages.observabilityCollector.startTime', 'Start time'),
        dataIndex: 'timestamp',
        width: 180,
        search: false,
        renderText: (value) => dayjs(value).format('MM-DD HH:mm:ss.SSS'),
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
                width: durationBarWidth(record.duration, maxPageDuration),
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
    [maxPageDuration, styles, t],
  );

  const applyFilters = (values: TraceFilters) => {
    filtersRef.current = values;
    setFilters(values);
    actionRef.current?.reload();
  };

  return (
    <div className={styles.panel}>
      <OverviewStats
        items={[
          {
            title: t('pages.observabilityCollector.traceTotal', 'Trace 总数'),
            value: traceTotal,
            hint: t(
              'pages.observabilityCollector.currentQueryWindow',
              '当前查询时间窗口',
            ),
          },
          {
            title: t('pages.observabilityCollector.traceServices', '上报服务'),
            value: services.length,
            hint:
              filters.serviceName ??
              t('pages.observabilityCollector.allServices', '全部服务'),
          },
          {
            title: t('pages.observabilityCollector.pageErrors', '本页异常'),
            value: pageRows.filter((row) => row.status === 'ERROR').length,
            hint: `${pageRows.length} ${t(
              'pages.observabilityCollector.pageRows',
              '条当前页记录',
            )}`,
            tone: pageRows.some((row) => row.status === 'ERROR')
              ? 'danger'
              : 'success',
          },
          {
            title: t('pages.observabilityCollector.pageSlowest', '本页最慢'),
            value: formatDuration(maxPageDuration),
            hint:
              pageRows.find((row) => row.duration === maxPageDuration)
                ?.serviceName ?? '-',
            tone: 'warning',
          },
        ]}
      />
      <Form
        form={form}
        layout="vertical"
        initialValues={filters}
        onFinish={applyFilters}
        className={styles.filterBar}
      >
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
                const nextFilters = {};
                form.resetFields();
                applyFilters(nextFilters);
              }}
            >
              {t('pages.observabilityCollector.reset', 'Reset')}
            </Button>
          </Space>
        </Form.Item>
      </Form>
      <div className={styles.tableWrap}>
        <ProTable<TraceRow>
          actionRef={actionRef}
          rowKey="traceId"
          columns={columns}
          search={false}
          size="small"
          options={{ reload: true, density: false, setting: false }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true }}
          request={async (params) => {
            const currentFilters = filtersRef.current;
            const [start, end] = timeRange;
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
            const rows = result.data ?? [];
            setPageRows(rows);
            setTraceTotal(result.total ?? 0);
            return {
              data: rows,
              success: result.code === 200,
              total: result.total ?? 0,
            };
          }}
        />
      </div>
      <TraceDetailDrawer
        clusterId={clusterId}
        traceId={drawerTraceId}
        open={!!drawerTraceId}
        onClose={() => setDrawerTraceId(undefined)}
        onShowLogs={(traceId) => {
          setDrawerTraceId(undefined);
          onShowLogs(traceId);
        }}
      />
    </div>
  );
};

export default TracesTab;
