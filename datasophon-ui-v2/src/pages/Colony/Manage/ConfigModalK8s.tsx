import Editor from '@monaco-editor/react';
import {
  ModalForm,
  ProFormDependency,
  ProFormItem,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import type { ProFormInstance } from '@ant-design/pro-components';
import { Button, message } from 'antd';
import React, { useCallback, useRef } from 'react';
import { getK8sConfig, saveK8sConfig, testK8sConnection } from '@/services/cluster';

const T_CONFIG_FILE = 'config_file';
const T_TOKEN = 'token';
const T_PASSWORD = 'password';

const CONNECTION_TYPE_OPTIONS = [
  { value: T_CONFIG_FILE, label: '配置文件' },
  { value: T_TOKEN, label: 'Token 认证' },
  { value: T_PASSWORD, label: '用户名/密码' },
];

const REQUIRED = [{ required: true }];

interface Props {
  cluster: DATASOPHON.ClusterResponse;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const ConfigModalK8s: React.FC<Props> = ({ cluster, open, onClose, onSuccess }) => {
  const formRef = useRef<ProFormInstance>(undefined);

  const handleTest = useCallback(async () => {
    try {
      const values = await formRef.current?.validateFields();
      if (!values) return;
      const res = await testK8sConnection({ ...values, clusterId: cluster.id });
      if (res.data?.success === false) {
        message.error(res.data.info ?? '连接测试失败');
      } else {
        message.success('连接测试成功');
      }
    } catch {
      // form validation error — antd already shows inline messages
    }
  }, [cluster.id]);

  const handleFinish = useCallback(
    async (values: DATASOPHON.K8sConfig) => {
      await saveK8sConfig({ ...values, clusterId: cluster.id });
      message.success('配置已保存');
      onSuccess();
      return true;
    },
    [cluster.id, onSuccess],
  );

  return (
    <ModalForm<DATASOPHON.K8sConfig>
      title={`K8s 集群配置 — ${cluster.clusterName}`}
      open={open}
      formRef={formRef}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
      onFinish={handleFinish}
      request={async () => {
        const res = await getK8sConfig(cluster.id);
        return { ...res.data, type: (res.data?.type ?? T_CONFIG_FILE) as DATASOPHON.K8sConnectType };
      }}
      modalProps={{ destroyOnClose: true, width: 640 }}
      submitter={{
        render: (_, doms) => (
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button key="test" onClick={handleTest}>
              测试连通性
            </Button>
            {doms}
          </div>
        ),
        resetButtonProps: false,
        searchConfig: { submitText: '保存' },
      }}
    >
      <ProFormSelect
        name="type"
        label="连接方式"
        options={CONNECTION_TYPE_OPTIONS}
        rules={REQUIRED}
      />
      <ProFormDependency name={['type']}>
        {({ type }) => {
          if (type === T_CONFIG_FILE) {
            return (
              <ProFormItem
                name="kubeConfig"
                label="配置文件内容"
                rules={[{ required: true, message: '请输入 kubeconfig 内容' }]}
              >
                <Editor
                  height="300px"
                  language="yaml"
                  options={{ minimap: { enabled: false }, wordWrap: 'on', automaticLayout: true }}
                />
              </ProFormItem>
            );
          }
          return (
            <>
              <ProFormText name="serverHost" label="K8s 主机名称" rules={REQUIRED} />
              <ProFormTextArea name="serverCert" label="K8s 证书" rules={REQUIRED} />
              {type === T_TOKEN && (
                <ProFormTextArea name="token" label="Token" rules={REQUIRED} />
              )}
              {type === T_PASSWORD && (
                <>
                  <ProFormText name="username" label="用户名" rules={REQUIRED} />
                  <ProFormText.Password name="password" label="密码" rules={REQUIRED} />
                </>
              )}
            </>
          );
        }}
      </ProFormDependency>
    </ModalForm>
  );
};

export default ConfigModalK8s;
