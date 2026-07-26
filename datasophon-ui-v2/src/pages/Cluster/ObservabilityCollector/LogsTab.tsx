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
import { type LogRow, listLogs, listTraceServices } from './service';

interface LogsTabProps extends ObservabilityTabContext {
  clusterId: number;
  traceId?: string;
  onTraceIdConsumed: () => void;
}

interface LogFilters {
  serviceName?: string;
  severities?: string[];
  bodyKeyword?: string;
  traceId?: string;
}

const severityOptions = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'];

function toSeconds(value: ObservabilityTabContext['timeRange'][number]) {
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
  const [form] = Form.useForm<LogFilters>();
  const [filters, setFilters] = useState<LogFilters>({
    severities: ['INFO', 'WARN', 'ERROR'],
  });
  const filtersRef = useRef(filters);
  const [services, setServices] = useState<string[]>([]);
  const [logTotal, setLogTotal] = useState(0);
  const [pageRows, setPageRows] = useState<LogRow[]>([]);

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
    if (!traceId) return;
    const nextFilters = { ...filtersRef.current, traceId };
    form.setFieldsValue(nextFilters);
    filtersRef.current = nextFilters;
    setFilters(nextFilters);
    actionRef.current?.reload();
    onTraceIdConsumed();
  }, [form, onTraceIdConsumed, traceId]);

  const columns = useMemo<ProColumns<LogRow>[]>(
    () => [
      {
        title: t('pages.observabilityCollector.timestamp', 'Timestamp'),
        dataIndex: 'timestamp',
        width: 180,
        search: false,
        renderText: (value) => dayjs(value).format('HH:mm:ss.SSS'),
      },
      {
        title: t('pages.observabilityCollector.severity', 'Severity'),
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
        title: t('pages.observabilityCollector.service', 'Service'),
        dataIndex: 'serviceName',
        width: 160,
        search: false,
        render: (_, record) => (
          <Tag className={styles.serviceTag}>{record.serviceName}</Tag>
        ),
      },
      {
        title: t('pages.observabilityCollector.body', 'Body'),
        dataIndex: 'body',
        search: false,
        render: (_, record) => (
          <span
            style={{
              color: record.severityText === 'ERROR' ? '#cf1322' : undefined,
            }}
          >
            {record.body}
          </span>
        ),
      },
      {
        title: t('pages.observabilityCollector.traceId', 'TraceID'),
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
                const nextFilters = {
                  ...filtersRef.current,
                  traceId: record.traceId,
                };
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
    [form, styles, t],
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
      <OverviewStats
        items={[
          {
            title: t('pages.observabilityCollector.logTotal', '日志总数'),
            value: logTotal,
            hint: t(
              'pages.observabilityCollector.currentQueryWindow',
              '当前查询时间窗口',
            ),
          },
          {
            title: 'INFO',
            value: pageRows.filter((row) => row.severityText === 'INFO').length,
            hint: t(
              'pages.observabilityCollector.currentPageCount',
              '当前页数量',
            ),
          },
          {
            title: 'WARN',
            value: pageRows.filter((row) => row.severityText === 'WARN').length,
            hint: t(
              'pages.observabilityCollector.currentPageCount',
              '当前页数量',
            ),
            tone: 'warning',
          },
          {
            title: 'ERROR / FATAL',
            value: pageRows.filter((row) =>
              ['ERROR', 'FATAL'].includes(row.severityText),
            ).length,
            hint: filters.traceId
              ? t(
                  'pages.observabilityCollector.linkedTraceActive',
                  '已按 TraceID 关联筛选',
                )
              : t(
                  'pages.observabilityCollector.currentPageCount',
                  '当前页数量',
                ),
            tone: pageRows.some((row) =>
              ['ERROR', 'FATAL'].includes(row.severityText),
            )
              ? 'danger'
              : 'success',
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
          label={t('pages.observabilityCollector.severity', 'Severity')}
          name="severities"
          style={{ marginBottom: 0 }}
        >
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
        <Form.Item
          label={t('pages.observabilityCollector.bodySearch', 'Body search')}
          name="bodyKeyword"
          style={{ marginBottom: 0 }}
        >
          <Input
            placeholder={t(
              'pages.observabilityCollector.bodyKeywordPlaceholder',
              'Search body keyword',
            )}
            style={{ width: 260 }}
          />
        </Form.Item>
        <Form.Item
          label={t('pages.observabilityCollector.traceId', 'TraceID')}
          name="traceId"
          style={{ marginBottom: 0 }}
        >
          <Input
            placeholder={t('pages.observabilityCollector.traceId', 'TraceID')}
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
                const nextFilters = {
                  severities: ['INFO', 'WARN', 'ERROR'],
                };
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
          {t('pages.observabilityCollector.severityQuick', 'Severity:')}
        </span>
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
      <div className={styles.tableWrap}>
        <ProTable<LogRow>
          actionRef={actionRef}
          rowKey={(record) =>
            `${record.timestamp}-${record.traceId}-${record.spanId}`
          }
          columns={columns}
          search={false}
          size="small"
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
            const [start, end] = timeRange;
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
            const rows = result.data ?? [];
            setPageRows(rows);
            setLogTotal(result.total ?? 0);
            return {
              data: rows,
              success: result.code === 200,
              total: result.total ?? 0,
            };
          }}
        />
      </div>
    </div>
  );
};

export default LogsTab;
