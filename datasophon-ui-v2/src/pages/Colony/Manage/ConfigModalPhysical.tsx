import { ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { Button, Form, Modal, Progress, Steps, Table, Tag, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import {
  analyzePhysicalHosts,
  checkPhysicalHostsCompleted,
  dispatchPhysicalWorkers,
  getPhysicalClusterInitializationStatus,
  type PhysicalHostConnection,
  type PhysicalHostInstallProgress,
  type PhysicalInitializationNode,
  type PhysicalInitializationStatus,
  retryPhysicalWorker,
  startPhysicalClusterInitialization,
} from '@/services/physicalHostInstall';

interface Props {
  cluster: DATASOPHON.ClusterResponse;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const CHECK_SUCCESS = 10001;
const INSTALL_FAILED = 3;

const CHECK_COLUMNS = [
  { title: '主机名', dataIndex: 'hostname' },
  { title: 'IP 地址', dataIndex: 'ip' },
  {
    title: '环境校验',
    dataIndex: ['checkResult', 'msg'],
    render: (_: unknown, record: PhysicalHostInstallProgress) =>
      record.checkResult?.code === CHECK_SUCCESS
        ? '通过'
        : (record.checkResult?.msg ?? '校验中'),
  },
];

const ConfigModalPhysical: React.FC<Props> = ({ cluster, open, onClose, onSuccess }) => {
  const [form] = Form.useForm<PhysicalHostConnection>();
  const [current, setCurrent] = useState(0);
  const [connection, setConnection] = useState<PhysicalHostConnection>();
  const [hosts, setHosts] = useState<PhysicalHostInstallProgress[]>([]);
  const [workers, setWorkers] = useState<PhysicalHostInstallProgress[]>([]);
  const [loading, setLoading] = useState(false);
  const [dispatchStarted, setDispatchStarted] = useState(false);
  const [initialization, setInitialization] = useState<PhysicalInitializationStatus>();

  const refreshHosts = useCallback(async () => {
    if (!connection) return;
    const result = await analyzePhysicalHosts(cluster.id, connection);
    setHosts(result.data ?? []);
  }, [cluster.id, connection]);

  const refreshWorkers = useCallback(async () => {
    const result = await dispatchPhysicalWorkers(cluster.id);
    setWorkers(result.data ?? []);
  }, [cluster.id]);

  const refreshInitialization = useCallback(async () => {
    const result = await getPhysicalClusterInitializationStatus(cluster.id);
    const status = result.data;
    setInitialization(status);
    if (status?.completed) {
      setCurrent(4);
    } else if (status && status.phase !== 'READY') {
      setCurrent(3);
    } else if (status?.nodes.length && status.nodes.every((node) => node.workerHealthy)) {
      setWorkers(
        status.nodes.map((node, index) => ({
          id: index + 1,
          hostname: node.hostname,
          ip: node.ip,
          progress: 100,
          message: 'Worker 已注册并通过 IP 健康检查',
        })),
      );
      setCurrent(2);
      setDispatchStarted(false);
    }
    return status;
  }, [cluster.id]);

  useEffect(() => {
    if (!open) return;
    refreshInitialization().catch(() => undefined);
  }, [open, refreshInitialization]);

  useEffect(() => {
    if (current !== 1 || !connection) return undefined;
    const timer = window.setInterval(() => {
      refreshHosts().catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [connection, current, refreshHosts]);

  useEffect(() => {
    if (current !== 2 || !dispatchStarted) return undefined;
    const timer = window.setInterval(() => {
      refreshWorkers().catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [current, dispatchStarted, refreshWorkers]);

  useEffect(() => {
    if (current !== 3) return undefined;
    let cancelled = false;
    let timer: number | undefined;
    const poll = async () => {
      try {
        await refreshInitialization();
      } catch {
        // 下一轮继续尝试。
      }
      if (!cancelled) {
        timer = window.setTimeout(poll, 3000);
      }
    };
    timer = window.setTimeout(poll, 3000);
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [current, refreshInitialization]);

  const reset = () => {
    form.resetFields();
    setCurrent(0);
    setConnection(undefined);
    setHosts([]);
    setWorkers([]);
    setDispatchStarted(false);
    setInitialization(undefined);
  };

  const close = () => {
    reset();
    onClose();
  };

  const startHostCheck = async () => {
    const values = await form.validateFields();
    const nextConnection = { ...values, sshPort: Number(values.sshPort) };
    setLoading(true);
    try {
      const result = await analyzePhysicalHosts(cluster.id, nextConnection);
      setConnection(nextConnection);
      setHosts(result.data ?? []);
      setCurrent(1);
    } finally {
      setLoading(false);
    }
  };

  const startWorkerDispatch = async () => {
    setLoading(true);
    try {
      const completed = await checkPhysicalHostsCompleted(cluster.id);
      if (!completed.hostCheckCompleted) {
        message.warning('请等待所有主机环境校验通过后再分发 Worker');
        await refreshHosts();
        return;
      }
      await refreshWorkers();
      setDispatchStarted(true);
      setCurrent(2);
    } finally {
      setLoading(false);
    }
  };

  const startCollector = async () => {
    setLoading(true);
    try {
      const result = await startPhysicalClusterInitialization(cluster.id);
      setInitialization(result.data);
      setCurrent(result.data?.completed ? 4 : 3);
      message.success('Worker 检查通过，已开始安装 OpenTelemetry Collector');
    } finally {
      setLoading(false);
    }
  };

  const finish = () => {
    message.success('Worker 与 OpenTelemetry Collector 初始化检查全部通过');
    close();
    onSuccess();
  };

  const retryInitialization = async () => {
    setLoading(true);
    try {
      const result = await startPhysicalClusterInitialization(cluster.id);
      setInitialization(result.data);
      message.success('已重新提交 Collector 初始化任务');
    } finally {
      setLoading(false);
    }
  };

  const initializationColumns = [
    { title: '主机名', dataIndex: 'hostname' },
    { title: 'IP 地址', dataIndex: 'ip' },
    {
      title: 'Worker',
      dataIndex: 'workerHealthy',
      render: (healthy: boolean) => (
        <Tag color={healthy ? 'success' : 'error'}>{healthy ? '正常' : '异常'}</Tag>
      ),
    },
    {
      title: 'Collector 安装',
      dataIndex: 'collectorInstalled',
      render: (installed: boolean) => (
        <Tag color={installed ? 'success' : 'processing'}>{installed ? '已安装' : '处理中'}</Tag>
      ),
    },
    {
      title: 'Collector 健康',
      dataIndex: 'collectorHealthy',
      render: (healthy: boolean) => (
        <Tag color={healthy ? 'success' : 'default'}>{healthy ? '正常' : '待检查'}</Tag>
      ),
    },
    { title: '状态信息', dataIndex: 'message' },
  ];

  const retryWorker = async (hostname: string) => {
    setLoading(true);
    try {
      await retryPhysicalWorker(cluster.id, hostname);
      await refreshWorkers();
    } finally {
      setLoading(false);
    }
  };

  const workerColumns = [
    { title: '主机名', dataIndex: 'hostname' },
    {
      title: '分发进度',
      dataIndex: 'progress',
      render: (_: unknown, record: PhysicalHostInstallProgress) => (
        <Progress
          percent={record.progress ?? 0}
          status={record.installStateCode === INSTALL_FAILED ? 'exception' : 'active'}
        />
      ),
    },
    { title: '状态信息', dataIndex: 'message' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: PhysicalHostInstallProgress) =>
        record.installStateCode === INSTALL_FAILED ? (
          <a onClick={() => retryWorker(record.hostname)}>重试</a>
        ) : null,
    },
  ];

  const renderFooter = () => {
    switch (current) {
      case 0:
        return [
          <Button key="cancel" onClick={close}>取消</Button>,
          <Button key="next" type="primary" loading={loading} onClick={startHostCheck}>开始环境校验</Button>,
        ];
      case 1:
        return [
          <Button key="prev" onClick={() => setCurrent(0)}>上一步</Button>,
          <Button key="next" type="primary" loading={loading} onClick={startWorkerDispatch}>分发 Worker</Button>,
        ];
      case 2:
        return [
          <Button key="close" onClick={close}>取消</Button>,
          <Button key="collector" type="primary" loading={loading} onClick={startCollector}>安装 Collector</Button>,
        ];
      case 3: {
        const buttons = [<Button key="close" onClick={close}>关闭</Button>];
        if (initialization?.canRetry) {
          buttons.push(
            <Button key="retry" type="primary" loading={loading} onClick={retryInitialization}>重试初始化</Button>,
          );
        }
        return buttons;
      }
      default:
        return [<Button key="finish" type="primary" onClick={finish}>完成初始化</Button>];
    }
  };

  return (
    <Modal
      title={`配置物理集群 — ${cluster.clusterName}`}
      open={open}
      onCancel={close}
      width={900}
      destroyOnHidden
      footer={renderFooter()}
    >
      <Steps
        current={current}
        items={[
          { title: '主机信息' },
          { title: '环境校验' },
          { title: 'Worker 分发' },
          { title: 'Collector 安装' },
          { title: '健康检查' },
        ]}
        style={{ marginBottom: 24 }}
      />
      {current === 0 && (
        <Form form={form} layout="vertical" initialValues={{ sshUser: 'root', sshPort: 22 }}>
          <ProFormTextArea
            name="hosts"
            label="主机列表"
            fieldProps={{ rows: 4 }}
            placeholder="以逗号分隔，例如：192.168.10.131,192.168.10.132,192.168.10.133"
            rules={[{ required: true, message: '请输入主机列表' }]}
          />
          <ProFormText name="sshUser" label="SSH 用户名" rules={[{ required: true, message: '请输入 SSH 用户名' }]} />
          <ProFormText.Password name="sshPass" label="SSH 密码" />
          <ProFormText
            name="sshPort"
            label="SSH 端口"
            rules={[{ required: true, message: '请输入 SSH 端口' }]}
          />
        </Form>
      )}
      {current === 1 && (
        <Table rowKey="hostname" columns={CHECK_COLUMNS} dataSource={hosts} pagination={false} />
      )}
      {current === 2 && (
        <Table rowKey="hostname" columns={workerColumns} dataSource={workers} pagination={false} />
      )}
      {(current === 3 || current === 4) && (
        <Table<PhysicalInitializationNode>
          rowKey="hostname"
          columns={initializationColumns}
          dataSource={initialization?.nodes ?? []}
          pagination={false}
        />
      )}
    </Modal>
  );
};

export default ConfigModalPhysical;
