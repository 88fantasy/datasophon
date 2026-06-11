import { Alert, Tag } from 'antd';
import type React from 'react';

interface Props {
  services: DATASOPHON.FrameService[];
}

/** 步骤 6：安装确认页，点击底部「开始安装」触发安装并跳转 DAG 图。 */
const StepInstall: React.FC<Props> = ({ services }) => {
  return (
    <div style={{ padding: '16px 0' }}>
      <Alert
        type="info"
        showIcon
        message="确认无误后点击「开始安装」，系统将生成安装 DAG 并立即执行，随后自动跳转到 DAG 进度图。"
        style={{ marginBottom: 16 }}
      />
      <div>
        <span style={{ marginRight: 8 }}>将安装以下服务：</span>
        {services.map((svc) => (
          <Tag color="blue" key={svc.id}>
            {svc.label || svc.serviceName}
          </Tag>
        ))}
      </div>
    </div>
  );
};

export default StepInstall;
