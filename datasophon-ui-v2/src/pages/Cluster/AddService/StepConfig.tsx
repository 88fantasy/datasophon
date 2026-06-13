import { Empty, Spin, Tabs } from 'antd';
import React, { useEffect, useState } from 'react';
import ConfigForm from '@/pages/Cluster/ServiceInstance/Setting/ConfigForm';
import { invokeHandleTemplateData } from '@/pages/Cluster/ServiceInstance/Setting/configTransform';
import { getConfigFromDdl } from '@/services/datasophon/addService';

interface Props {
  clusterId: number;
  services: DATASOPHON.FrameService[];
  active: boolean;
  /** 容器持有的原始（未转换）配置 ref，提交时用于 invokeFormatTemplateData 还原 */
  rawConfigMapRef: React.MutableRefObject<
    Record<string, DATASOPHON.ConfigField[]>
  >;
}

/** 步骤 5：按服务分 Tab 渲染 DDL 配置表单（复用切片 4b 的 ConfigForm）。 */
const StepConfig: React.FC<Props> = ({
  clusterId,
  services,
  active,
  rawConfigMapRef,
}) => {
  const [templateMap, setTemplateMap] = useState<
    Record<string, DATASOPHON.ConfigField[]>
  >({});
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!active || services.length === 0) return;
    let cancelled = false;
    setLoading(true);

    Promise.all(
      services.map((svc) =>
        getConfigFromDdl(clusterId, svc.serviceName).then((res) => ({
          serviceName: svc.serviceName,
          raw: res.data ?? [],
        })),
      ),
    )
      .then((results) => {
        if (cancelled) return;
        const rawMap: Record<string, DATASOPHON.ConfigField[]> = {};
        const converted: Record<string, DATASOPHON.ConfigField[]> = {};
        for (const { serviceName, raw } of results) {
          rawMap[serviceName] = raw;
          converted[serviceName] = invokeHandleTemplateData(raw);
        }
        rawConfigMapRef.current = rawMap;
        setTemplateMap(converted);
      })
      .catch(() => {
        /* 全局错误处理已弹消息 */
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [active, clusterId, services, rawConfigMapRef]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 0' }}>
        <Spin />
      </div>
    );
  }

  if (services.length === 0 || Object.keys(templateMap).length === 0) {
    return <Empty description="暂无配置项" style={{ padding: '32px 0' }} />;
  }

  return (
    <div style={{ maxHeight: '50vh', overflow: 'auto', paddingRight: 8 }}>
      <Tabs
        items={services.map((svc) => ({
          key: svc.serviceName,
          label: svc.label || svc.serviceName,
          // forceRender 确保所有服务的字段注册进表单，
          // 否则未访问 Tab 的配置在 onFinish values 中缺失，保存时会覆盖默认值
          forceRender: true,
          children: (
            <ConfigForm
              templateData={templateMap[svc.serviceName] ?? []}
              namePrefix={[svc.serviceName]}
            />
          ),
        }))}
      />
    </div>
  );
};

export default StepConfig;
