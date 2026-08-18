import type { ProColumns } from '@ant-design/pro-components';
import { ProCard, ProTable } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  message,
  Space,
  Steps,
  Tag,
  Typography,
} from 'antd';
import React, { useContext, useMemo, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import {
  listDorisCandidates,
  registerTakeover,
  saveDorisDatasource,
  scanTakeover,
  testDorisDatasource,
} from '@/services/k8s';

const { Text, Paragraph } = Typography;

const SOURCE_LABEL: Record<string, string> = {
  LOAD_BALANCER: 'LoadBalancer',
  NODE_PORT: 'NodePort',
  CLUSTER_IP: 'ClusterIP',
};

const unwrap = <T,>(res: unknown): T | undefined =>
  (res as { data?: T })?.data ?? (res as T);

/**
 * K8s 集群接管向导。
 *
 * 三步：配置 Doris 数据源 → 扫描并确认服务绑定 → 查看登记结果。
 * 全程只读目标集群，不下发任何变更。
 */
const Takeover: React.FC = () => {
  const context = useContext(ClusterContext);
  if (!context)
    throw new Error('Takeover must be rendered inside ClusterLayout');
  const { clusterId } = context;

  const [step, setStep] = useState(0);
  const [form] = Form.useForm();
  const [candidates, setCandidates] = useState<
    DATASOPHON.DorisDatasourceCandidate[]
  >([]);
  const [discovering, setDiscovering] = useState(false);
  const [testing, setTesting] = useState(false);
  const [savingDatasource, setSavingDatasource] = useState(false);

  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] =
    useState<DATASOPHON.K8sTakeoverScanResult>();
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [registering, setRegistering] = useState(false);
  const [registerResult, setRegisterResult] =
    useState<DATASOPHON.K8sTakeoverRegisterResult[]>();

  // ── 第 1 步：Doris 数据源 ──────────────────────────────────
  const handleDiscover = async () => {
    setDiscovering(true);
    try {
      const res = await listDorisCandidates(clusterId);
      const items = unwrap<DATASOPHON.DorisDatasourceCandidate[]>(res) ?? [];
      setCandidates(items);
      // 自动填入第一个可达候选；不可达的只展示不填，避免用户误提交
      const reachable = items.find((c) => c.reachable);
      if (reachable) {
        form.setFieldsValue({ host: reachable.host, port: reachable.port });
        message.success(`已发现 ${items.length} 个候选，已填入可达地址`);
      } else if (items.length) {
        message.warning(
          `发现 ${items.length} 个候选，但均非平台可直连地址，请手工填写`,
        );
      } else {
        message.warning('未发现暴露 9030 端口的 Service，请手工填写');
      }
    } finally {
      setDiscovering(false);
    }
  };

  const handleTest = async () => {
    const values = await form.validateFields();
    setTesting(true);
    try {
      await testDorisDatasource(clusterId, values);
      message.success('连接成功');
    } finally {
      setTesting(false);
    }
  };

  const handleSaveDatasource = async () => {
    const values = await form.validateFields();
    setSavingDatasource(true);
    try {
      await saveDorisDatasource(clusterId, values);
      message.success('数据源已保存');
      setStep(1);
    } finally {
      setSavingDatasource(false);
    }
  };

  // ── 第 2 步：扫描与确认 ────────────────────────────────────
  const handleScan = async () => {
    setScanning(true);
    try {
      const res = await scanTakeover(clusterId);
      const result = unwrap<DATASOPHON.K8sTakeoverScanResult>(res);
      setScanResult(result);
      // 已自动匹配且尚未接管的默认全选；重扫时已接管的不再重复勾选
      setSelectedKeys(
        (result?.matched ?? [])
          .filter((r) => !r.registered)
          .map((r) => r.releaseName),
      );
    } finally {
      setScanning(false);
    }
  };

  const handleRegister = async () => {
    const chosen = (scanResult?.matched ?? []).filter((r) =>
      selectedKeys.includes(r.releaseName),
    );
    if (!chosen.length) {
      message.warning('请至少选择一个服务');
      return;
    }
    setRegistering(true);
    try {
      const res = await registerTakeover(
        clusterId,
        chosen.map((r) => ({
          releaseName: r.releaseName,
          namespace: r.namespace,
          frameServiceId: r.frameServiceId as number,
        })),
      );
      setRegisterResult(
        unwrap<DATASOPHON.K8sTakeoverRegisterResult[]>(res) ?? [],
      );
      message.success(`已接管 ${chosen.length} 个服务`);
      setStep(2);
    } finally {
      setRegistering(false);
    }
  };

  const scanColumns: ProColumns<DATASOPHON.ScannedRelease>[] = useMemo(
    () => [
      { title: 'Helm Release', dataIndex: 'releaseName', width: 180 },
      { title: '命名空间', dataIndex: 'namespace', width: 130 },
      {
        title: 'Chart',
        dataIndex: 'chart',
        width: 220,
        render: (_, r) => (
          <Text type="secondary">
            {r.chartName}
            {r.chartVersion ? ` · ${r.chartVersion}` : ''}
          </Text>
        ),
      },
      {
        title: '绑定框架服务',
        dataIndex: 'frameServiceName',
        render: (_, r) =>
          r.frameServiceName ? (
            <Space>
              <Text>{r.frameServiceName}</Text>
              {r.catalog ? <Tag>{r.catalog}</Tag> : null}
            </Space>
          ) : (
            <Tag color="warning">待人工绑定</Tag>
          ),
      },
    ],
    [],
  );

  const candidateColumns: ProColumns<DATASOPHON.DorisDatasourceCandidate>[] =
    useMemo(
      () => [
        { title: 'Service', dataIndex: 'serviceName' },
        { title: '命名空间', dataIndex: 'namespace', width: 120 },
        { title: '类型', dataIndex: 'serviceType', width: 130 },
        {
          title: '地址',
          width: 200,
          render: (_, c) => `${c.host ?? '-'}:${c.port ?? '-'}`,
        },
        {
          title: '来源',
          dataIndex: 'source',
          width: 130,
          render: (_, c) => SOURCE_LABEL[c.source] ?? c.source,
        },
        {
          title: '平台可直连',
          dataIndex: 'reachable',
          width: 110,
          render: (_, c) =>
            c.reachable ? (
              <Tag color="success">是</Tag>
            ) : (
              <Tag color="default">否</Tag>
            ),
        },
        { title: '说明', dataIndex: 'hint', ellipsis: true },
      ],
      [],
    );

  return (
    <ProCard>
      <Steps
        current={step}
        items={[
          { title: '配置监控数据源' },
          { title: '扫描并确认服务' },
          { title: '接管结果' },
        ]}
        style={{ marginBottom: 24 }}
      />

      {step === 0 && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="接管为只读模式"
            description="平台只读取该集群的服务信息与监控数据，不会下发任何变更。看板数据来自集群自身的 Doris（OTel 库）。"
          />
          <Space style={{ marginBottom: 16 }}>
            <Button onClick={handleDiscover} loading={discovering}>
              自动发现 Doris 地址
            </Button>
            <Text type="secondary">
              发现结果仅供参考，能否直连以「测试连接」为准
            </Text>
          </Space>

          {candidates.length > 0 && (
            <ProTable<DATASOPHON.DorisDatasourceCandidate>
              rowKey={(r) => `${r.namespace}/${r.serviceName}/${r.port}`}
              columns={candidateColumns}
              dataSource={candidates}
              search={false}
              options={false}
              pagination={false}
              size="small"
              style={{ marginBottom: 16 }}
            />
          )}

          <Form
            form={form}
            layout="vertical"
            initialValues={{
              port: 9030,
              database: 'otel',
              username: 'otel_reader',
            }}
            style={{ maxWidth: 520 }}
          >
            <Form.Item
              name="host"
              label="Doris FE 主机"
              rules={[
                { required: true, message: '请输入平台可直连的主机地址' },
              ]}
            >
              <Input placeholder="平台可直连的地址，如 10.0.0.9" />
            </Form.Item>
            <Form.Item name="port" label="MySQL 协议端口">
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="database" label="OTel 数据库名">
              <Input />
            </Form.Item>
            <Form.Item name="username" label="只读账号">
              <Input />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入只读账号密码' }]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Space>
              <Button onClick={handleTest} loading={testing}>
                测试连接
              </Button>
              <Button
                type="primary"
                onClick={handleSaveDatasource}
                loading={savingDatasource}
              >
                保存并下一步
              </Button>
            </Space>
            <Paragraph type="secondary" style={{ marginTop: 12 }}>
              保存前会强制做一次连通性测试，不通过不会写入。
            </Paragraph>
          </Form>
        </>
      )}

      {step === 1 && (
        <>
          <Space style={{ marginBottom: 16 }}>
            <Button type="primary" onClick={handleScan} loading={scanning}>
              扫描集群现有服务
            </Button>
            {scanResult && (
              <Text type="secondary">
                已匹配 {scanResult.matched.length} 个，待人工绑定{' '}
                {scanResult.pending.length} 个
              </Text>
            )}
          </Space>

          {scanResult && (
            <>
              {(scanResult.missing?.length ?? 0) > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message={`有 ${scanResult.missing?.length} 个已接管的服务在集群中已不存在`}
                  description={
                    <>
                      {scanResult.missing
                        ?.map((m) => `${m.namespace}/${m.releaseName}`)
                        .join('、')}
                      。平台不会自动删除登记——若确认不再需要，请到对应服务页「取消接管」。
                    </>
                  }
                />
              )}
              <ProTable<DATASOPHON.ScannedRelease>
                headerTitle="已匹配的服务"
                rowKey="releaseName"
                columns={scanColumns}
                dataSource={scanResult.matched}
                search={false}
                options={false}
                pagination={false}
                size="small"
                rowSelection={{
                  selectedRowKeys: selectedKeys,
                  onChange: setSelectedKeys,
                  // 已接管的不给再勾一次，避免重复登记
                  getCheckboxProps: (record) => ({
                    disabled: Boolean(record.registered),
                  }),
                }}
              />
              {scanResult.pending.length > 0 && (
                <ProTable<DATASOPHON.ScannedRelease>
                  headerTitle="未匹配到框架服务定义"
                  rowKey="releaseName"
                  columns={scanColumns}
                  dataSource={scanResult.pending}
                  search={false}
                  options={false}
                  pagination={false}
                  size="small"
                  style={{ marginTop: 16 }}
                />
              )}
              <Space style={{ marginTop: 16 }}>
                <Button onClick={() => setStep(0)}>上一步</Button>
                <Button
                  type="primary"
                  onClick={handleRegister}
                  loading={registering}
                  disabled={!selectedKeys.length}
                >
                  接管选中的 {selectedKeys.length} 个服务
                </Button>
              </Space>
            </>
          )}
        </>
      )}

      {step === 2 && registerResult && (
        <>
          <Alert
            type="success"
            showIcon
            style={{ marginBottom: 16 }}
            message={`已接管 ${registerResult.length} 个服务`}
            description="未接入采集的服务可以正常查看资源信息，但监控看板无数据，需在集群侧补充 ServiceMonitor。"
          />
          <ProTable<DATASOPHON.K8sTakeoverRegisterResult>
            rowKey="instanceId"
            search={false}
            options={false}
            pagination={false}
            size="small"
            dataSource={registerResult}
            columns={[
              { title: 'Helm Release', dataIndex: 'releaseName', width: 200 },
              { title: '命名空间', dataIndex: 'namespace', width: 140 },
              {
                title: '监控采集',
                dataIndex: 'scraped',
                width: 120,
                render: (_, r) =>
                  r.scraped ? (
                    <Tag color="success">已接入</Tag>
                  ) : (
                    <Tag color="warning">未接入</Tag>
                  ),
              },
              {
                title: 'OTel Job',
                dataIndex: 'metricsJob',
                render: (_, r) =>
                  r.metricsJob ? (
                    <Space size={[0, 4]} wrap>
                      {r.metricsJob.split(',').map((job) => (
                        <Tag key={job}>{job}</Tag>
                      ))}
                    </Space>
                  ) : (
                    <Text type="secondary">-</Text>
                  ),
              },
            ]}
          />
          <Descriptions style={{ marginTop: 16 }} column={1} size="small">
            <Descriptions.Item label="后续">
              可在左侧服务列表中查看已接管的服务，已接入采集的服务可直接查看监控看板。
            </Descriptions.Item>
          </Descriptions>
        </>
      )}
    </ProCard>
  );
};

export default Takeover;
