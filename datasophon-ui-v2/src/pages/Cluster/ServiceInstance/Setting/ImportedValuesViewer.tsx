/**
 * 接管实例的配置只读展示。
 *
 * 与 HelmEditor 的关键区别：接管实例在 t_ddh_k8s_service_instance_values 里**没有任何记录**
 * （平台从未替它写过配置），所以不能走版本列表那套；这里直接反查 `helm get values`，
 * 拿到目标集群里的现状，只看不改。
 */

import Editor from '@monaco-editor/react';
import { Alert, Spin } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { readTakeoverValues } from '@/services/k8s';

interface ImportedValuesViewerProps {
  clusterId: number;
  instanceId: number;
  releaseName?: string;
}

/** helm 返回 JSON 文本，格式化后更易读；解析失败就原样显示。 */
function prettify(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

const ImportedValuesViewer: React.FC<ImportedValuesViewerProps> = ({
  clusterId,
  instanceId,
  releaseName,
}) => {
  const [values, setValues] = useState('');
  const [loading, setLoading] = useState(true);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    setLoading(true);
    readTakeoverValues(clusterId, instanceId)
      .then((res: any) => {
        if (!mountedRef.current) return;
        setValues(prettify(res?.data ?? ''));
      })
      .finally(() => {
        if (mountedRef.current) setLoading(false);
      });
    return () => {
      mountedRef.current = false;
    };
  }, [clusterId, instanceId]);

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="接管的服务只读展示配置"
        description={
          releaseName
            ? `内容来自目标集群 helm get values ${releaseName}，平台不会修改它。如需变更请在目标集群自行操作。`
            : '内容来自目标集群的 helm get values，平台不会修改它。如需变更请在目标集群自行操作。'
        }
      />
      <Spin spinning={loading}>
        <div
          style={{
            border: '1px solid #e8e8e8',
            borderRadius: 4,
            overflow: 'hidden',
          }}
        >
          <Editor
            height="60vh"
            language="json"
            value={values}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false,
              fontSize: 13,
            }}
          />
        </div>
      </Spin>
    </div>
  );
};

export default ImportedValuesViewer;
