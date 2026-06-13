/**
 * K8s Helm 配置双栏编辑器。
 *
 * 左栏：可编辑 deltaValues（用户新增/覆盖的 yaml）。
 * 右栏：只读预览 —— metaFileType 含 "helm" 时显示 mergeYamlFiles(values, delta)，
 *        否则直接显示原始 values。
 *
 * 保存语义：仅更新 deltaValues，不升版、不打 needRestart（与后端 update 接口对齐）。
 */

import Editor from '@monaco-editor/react';
import { Button, message, Select, Spin } from 'antd';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  getK8sConfig,
  listK8sConfigVersions,
  saveK8sConfig,
} from '@/services/service';
import { mergeYamlFiles } from './yamlMerge';

interface HelmEditorProps {
  clusterId: number;
  instanceId: number;
}

const HelmEditor: React.FC<HelmEditorProps> = ({ clusterId, instanceId }) => {
  const [versions, setVersions] = useState<DATASOPHON.K8sInstanceValuesSimple[]>([]);
  const [selectedVersionId, setSelectedVersionId] = useState<number | undefined>(undefined);
  const [helmValues, setHelmValues] = useState<DATASOPHON.K8sInstanceValues | null>(null);

  /** 用户在左栏编辑的 deltaValues（未保存） */
  const [draftDelta, setDraftDelta] = useState('');

  const [versionsLoading, setVersionsLoading] = useState(true);
  const [configLoading, setConfigLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const mountedRef = useRef(true);

  // ─── 加载版本列表 ─────────────────────────────────────────────────────
  useEffect(() => {
    mountedRef.current = true;
    setVersionsLoading(true);
    listK8sConfigVersions(clusterId, instanceId)
      .then((res: any) => {
        if (!mountedRef.current) return;
        const list: DATASOPHON.K8sInstanceValuesSimple[] = res?.data ?? [];
        setVersions(list);
        if (list.length > 0) {
          setSelectedVersionId(list[0].id);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (mountedRef.current) setVersionsLoading(false);
      });
    return () => {
      mountedRef.current = false;
    };
  }, [clusterId, instanceId]);

  // ─── 按版本加载完整 Helm values ──────────────────────────────────────
  const loadConfig = useCallback(
    (valueId: number) => {
      setConfigLoading(true);
      getK8sConfig(clusterId, instanceId, valueId)
        .then((res: any) => {
          if (!mountedRef.current) return;
          const data: DATASOPHON.K8sInstanceValues = res?.data;
          setHelmValues(data);
          setDraftDelta(data?.deltaValues ?? '');
        })
        .catch(() => {})
        .finally(() => {
          if (mountedRef.current) setConfigLoading(false);
        });
    },
    [clusterId, instanceId],
  );

  useEffect(() => {
    if (selectedVersionId != null) {
      loadConfig(selectedVersionId);
    }
  }, [selectedVersionId, loadConfig]);

  // ─── 右栏合并预览 ─────────────────────────────────────────────────────
  const previewValue = useMemo(() => {
    if (!helmValues) return '';
    const isHelm = /helm/i.test(helmValues.metaFileType ?? '');
    if (isHelm && draftDelta.trim()) {
      return mergeYamlFiles(helmValues.values ?? '', draftDelta);
    }
    return helmValues.values ?? '';
  }, [helmValues, draftDelta]);

  // ─── 保存 ─────────────────────────────────────────────────────────────
  const onSave = async () => {
    if (helmValues == null) return;
    setSaving(true);
    try {
      await saveK8sConfig(clusterId, instanceId, {
        id: helmValues.id,
        deltaValues: draftDelta,
      });
      message.success('Helm 配置保存成功');
    } catch {
      /* global error handler */
    } finally {
      setSaving(false);
    }
  };

  if (versionsLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (versions.length === 0) {
    return (
      <div style={{ color: '#999', padding: '32px 0', textAlign: 'center' }}>
        暂无 Helm values 版本
      </div>
    );
  }

  return (
    <div style={{ padding: '16px 0' }}>
      {/* 版本选择器 + 保存按钮 */}
      <div
        style={{
          marginBottom: 12,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span style={{ flexShrink: 0 }}>Values 版本：</span>
        <Select
          style={{ width: 160 }}
          value={selectedVersionId}
          onChange={(v) => setSelectedVersionId(v)}
          options={versions.map((ver) => ({
            label: `版本 ${ver.version}`,
            value: ver.id,
          }))}
          loading={versionsLoading}
        />
        <Button
          type="primary"
          onClick={onSave}
          loading={saving}
          disabled={configLoading}
        >
          保存配置
        </Button>
      </div>

      {/* 双栏编辑器 */}
      <Spin spinning={configLoading}>
        <div style={{ display: 'flex', gap: 8 }}>
          {/* 左栏：可编辑 deltaValues */}
          <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 4, overflow: 'hidden' }}>
            <div
              style={{
                padding: '4px 8px',
                background: '#fafafa',
                borderBottom: '1px solid #e8e8e8',
                fontSize: 12,
                color: '#666',
              }}
            >
              Delta Values（可编辑）
            </div>
            <Editor
              height="60vh"
              language="yaml"
              value={draftDelta}
              onChange={(v) => setDraftDelta(v ?? '')}
              options={{
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                fontSize: 13,
              }}
            />
          </div>

          {/* 右栏：合并预览（只读） */}
          <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 4, overflow: 'hidden' }}>
            <div
              style={{
                padding: '4px 8px',
                background: '#fafafa',
                borderBottom: '1px solid #e8e8e8',
                fontSize: 12,
                color: '#666',
              }}
            >
              合并预览（只读）
            </div>
            <Editor
              height="60vh"
              language="yaml"
              value={previewValue}
              options={{
                readOnly: true,
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                fontSize: 13,
              }}
            />
          </div>
        </div>
      </Spin>
    </div>
  );
};

export default HelmEditor;
