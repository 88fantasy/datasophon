/**
 * 网关配置代码视图：Monaco 编辑 apisixGatewayYaml 用户可编辑段。
 *
 * plugin_metadata 与 #END 由模板固定输出、不在此编辑，故提供只读的
 * 「预览最终 apisix.yaml」抽屉：拼接 text + managedSuffix 展示真实落盘效果。
 */

import Editor from '@monaco-editor/react';
import { useIntl } from '@umijs/max';
import { Alert, Button, Drawer } from 'antd';
import React, { useState } from 'react';

interface CodeViewProps {
  text: string;
  onChange: (text: string) => void;
  managedSuffix: string;
}

const MONACO_YAML_OPTIONS = {
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 13,
};

const CodeView: React.FC<CodeViewProps> = ({
  text,
  onChange,
  managedSuffix,
}) => {
  const intl = useIntl();
  const [previewOpen, setPreviewOpen] = useState(false);

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 8 }}
        message={intl.formatMessage({
          id: 'pages.apisixGateway.banner.managedHint',
        })}
      />
      <div
        style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}
      >
        <Button onClick={() => setPreviewOpen(true)}>
          {intl.formatMessage({ id: 'pages.apisixGateway.preview.button' })}
        </Button>
      </div>
      <div
        style={{
          border: '1px solid #e8e8e8',
          borderRadius: 4,
          overflow: 'hidden',
        }}
      >
        <Editor
          height="60vh"
          language="yaml"
          value={text}
          onChange={(v) => onChange(v ?? '')}
          options={MONACO_YAML_OPTIONS}
        />
      </div>
      <Drawer
        title={intl.formatMessage({ id: 'pages.apisixGateway.preview.title' })}
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        width={720}
      >
        <Editor
          height="80vh"
          language="yaml"
          value={text + managedSuffix}
          options={{ ...MONACO_YAML_OPTIONS, readOnly: true }}
        />
      </Drawer>
    </div>
  );
};

export default CodeView;
