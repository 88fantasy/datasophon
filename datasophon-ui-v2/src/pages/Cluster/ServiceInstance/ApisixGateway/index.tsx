/**
 * APISIX(standalone) 网关配置 Tab —— 图形化 + 代码化双视图管理 upstreams/routes/global_rules。
 *
 * 真相源是隐藏参数 apisixGatewayYaml；图形化视图是它 load() 后的结构化投影，
 * 切换视图 = load/dump，不是两套数据（见 docs/apisix-gateway-tab-实施任务清单）。
 * 状态机语义（约束 2/3）：
 *  - 代码 → 图形化：先 load(text) 试解析，失败留在代码视图并报错，不静默降级。
 *  - 图形化 → 代码（未改动）：直接复用原始 text，不重新 dump，保住注释与排版。
 *  - 图形化 → 代码（已改动）：dump(doc)；若原 text 含注释，先二次确认告知注释将丢失。
 *  - 保存以当前所在视图为准：代码视图交 text，图形化视图交 dump(doc)。
 */

import { ProCard } from '@ant-design/pro-components';
import { Button, Modal, Segmented, message } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { useIntl } from '@umijs/max';
import { getApisixGateway, saveApisixGateway } from '@/services/service';
import CodeView from './CodeView';
import GraphicView from './GraphicView';
import {
  type ApisixGatewayDoc,
  dumpGatewayYaml,
  hasComments,
  loadGatewayYaml,
} from './gatewayYaml';

interface ApisixGatewayPanelProps {
  clusterId: number;
  instanceId: number;
}

type ViewMode = 'code' | 'graphic';

const ApisixGatewayPanel: React.FC<ApisixGatewayPanelProps> = ({
  clusterId,
  instanceId,
}) => {
  const intl = useIntl();
  const mountedRef = useRef(true);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [view, setView] = useState<ViewMode>('code');

  const [text, setText] = useState('');
  const [doc, setDoc] = useState<ApisixGatewayDoc>({});
  const [graphicDirty, setGraphicDirty] = useState(false);
  const [managedSuffix, setManagedSuffix] = useState('');

  useEffect(() => {
    mountedRef.current = true;
    setLoading(true);
    getApisixGateway(clusterId, instanceId)
      .then((res: any) => {
        if (!mountedRef.current) return;
        const data: DATASOPHON.ApisixGatewayResponse | undefined = res?.data;
        setText(data?.gatewayYaml ?? '');
        setManagedSuffix(data?.managedSuffix ?? '');
      })
      .catch(() => {})
      .finally(() => {
        if (mountedRef.current) setLoading(false);
      });
    return () => {
      mountedRef.current = false;
    };
  }, [clusterId, instanceId]);

  const switchToGraphic = () => {
    try {
      setDoc(loadGatewayYaml(text));
      setGraphicDirty(false);
      setView('graphic');
    } catch (e: any) {
      message.error(
        `${intl.formatMessage({ id: 'pages.apisixGateway.parseError' })}${
          e?.message ? `：${e.message}` : ''
        }`,
      );
    }
  };

  const applyGraphicToText = () => {
    setText(dumpGatewayYaml(doc));
    setGraphicDirty(false);
    setView('code');
  };

  const switchToCode = () => {
    if (!graphicDirty) {
      setView('code');
      return;
    }
    if (hasComments(text)) {
      Modal.confirm({
        title: intl.formatMessage({
          id: 'pages.apisixGateway.confirm.commentsLostTitle',
        }),
        content: intl.formatMessage({
          id: 'pages.apisixGateway.confirm.commentsLostContent',
        }),
        onOk: applyGraphicToText,
      });
      return;
    }
    applyGraphicToText();
  };

  const onSave = async () => {
    const payload = view === 'code' ? text : dumpGatewayYaml(doc);
    setSaving(true);
    try {
      await saveApisixGateway(clusterId, instanceId, payload);
      message.success(intl.formatMessage({ id: 'pages.apisixGateway.saveSuccess' }));
      setText(payload);
      setGraphicDirty(false);
    } catch {
      // 全局错误处理器已提示，这里不重复
    } finally {
      setSaving(false);
    }
  };

  return (
    <ProCard loading={loading}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: 12,
        }}
      >
        <Segmented
          value={view}
          onChange={(v) => (v === 'graphic' ? switchToGraphic() : switchToCode())}
          options={[
            {
              label: intl.formatMessage({ id: 'pages.apisixGateway.view.graphic' }),
              value: 'graphic',
            },
            {
              label: intl.formatMessage({ id: 'pages.apisixGateway.view.code' }),
              value: 'code',
            },
          ]}
        />
        <Button type="primary" loading={saving} onClick={onSave}>
          {intl.formatMessage({ id: 'pages.apisixGateway.save' })}
        </Button>
      </div>
      {view === 'code' ? (
        <CodeView text={text} onChange={setText} managedSuffix={managedSuffix} />
      ) : (
        <GraphicView
          doc={doc}
          onChange={(next) => {
            setDoc(next);
            setGraphicDirty(true);
          }}
        />
      )}
    </ProCard>
  );
};

export default ApisixGatewayPanel;
