/**
 * 服务配置编辑 Tab。
 *
 * - archType=k8s   → HelmEditor（Monaco 双栏，切片 4b-2 新增）
 * - archType=physical → PhysicalSettingContent（角色组菜单 + ProForm）
 */

import { ProForm } from '@ant-design/pro-components';
import type { MenuProps } from 'antd';
import { Menu, message, Select, Spin } from 'antd';
import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';

import ClusterContext from '@/context/ClusterContext';
import {
  getServiceConfig,
  getServiceRoleGroupList,
  listConfigVersions,
  saveServiceConfig,
} from '@/services/service';

import ConfigForm from './ConfigForm';
import {
  invokeFormatTemplateData,
  invokeHandleTemplateData,
} from './configTransform';
import HelmEditor from './HelmEditor';

interface SettingTabProps {
  clusterId: number;
  instanceId: number;
}

type RoleGroup = {
  id: number;
  roleGroupName: string;
  serviceInstanceId: number;
};

// ── 物理集群配置 ProForm（原 SettingTab 主体，无改动）────────────────────

const PhysicalSettingContent: React.FC<SettingTabProps> = ({ clusterId, instanceId }) => {
  const [roleGroups, setRoleGroups] = useState<RoleGroup[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const [versions, setVersions] = useState<number[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<number | undefined>(
    undefined,
  );

  /** 从后端拿到的原始（未转换）配置数据，提交时用于还原格式 */
  const [rawConfig, setRawConfig] = useState<DATASOPHON.ConfigField[]>([]);
  /** 经 invokeHandleTemplateData 转换后的数据，用作 ProForm initialValues */
  const [templateData, setTemplateData] = useState<DATASOPHON.ConfigField[]>(
    [],
  );

  const [configLoading, setConfigLoading] = useState(false);
  const [formKey, setFormKey] = useState(0); // 强制 ProForm 重置 initialValues

  const [formRef] = ProForm.useForm();
  const mountedRef = useRef(true);

  // ─── 加载角色组列表 ─────────────────────────────────────────────────────
  useEffect(() => {
    mountedRef.current = true;
    getServiceRoleGroupList(clusterId, instanceId)
      .then((res: any) => {
        if (!mountedRef.current) return;
        const data: RoleGroup[] = res?.data ?? [];
        setRoleGroups(data);
        if (data.length > 0) {
          setSelectedGroupId(data[0].id);
        }
      })
      .catch(() => {});
    return () => {
      mountedRef.current = false;
    };
  }, [clusterId, instanceId]);

  // ─── 加载版本列表（角色组切换时触发） ───────────────────────────────────
  const loadVersions = useCallback(
    (roleGroupId: number) => {
      listConfigVersions(clusterId, instanceId, roleGroupId)
        .then((res: any) => {
          if (!mountedRef.current) return;
          const vList: number[] = res?.data ?? [];
          setVersions(vList);
          // 默认选最新版（第一项）
          const latest = vList[0];
          setSelectedVersion(latest);
        })
        .catch(() => {});
    },
    [clusterId, instanceId],
  );

  useEffect(() => {
    if (selectedGroupId != null) {
      loadVersions(selectedGroupId);
    }
  }, [selectedGroupId, loadVersions]);

  // ─── 加载配置数据（角色组或版本变化时触发） ─────────────────────────────
  const loadConfig = useCallback(
    (roleGroupId: number, version?: number) => {
      setConfigLoading(true);
      getServiceConfig(clusterId, instanceId, roleGroupId, version)
        .then((res: any) => {
          if (!mountedRef.current) return;
          const raw: DATASOPHON.ConfigField[] = res?.data ?? [];
          setRawConfig(raw);
          setTemplateData(invokeHandleTemplateData(raw));
          // 递增 key 使 ProForm 以新 initialValues 重新渲染
          setFormKey((k) => k + 1);
        })
        .catch(() => {})
        .finally(() => {
          if (mountedRef.current) setConfigLoading(false);
        });
    },
    [clusterId, instanceId],
  );

  useEffect(() => {
    if (selectedGroupId != null && selectedVersion !== undefined) {
      loadConfig(selectedGroupId, selectedVersion);
    }
  }, [selectedGroupId, selectedVersion, loadConfig]);

  // ─── 版本切换（仅读回填，不另存） ─────────────────────────────────────
  const onVersionChange = (v: number) => {
    setSelectedVersion(v);
    // loadConfig 由 useEffect 依赖变化触发，无需手动调
  };

  // ─── 角色组菜单切换 ────────────────────────────────────────────────────
  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    const id = Number(key);
    setSelectedGroupId(id);
    setSelectedVersion(undefined); // 先清版本，等版本列表加载完再选最新
  };

  // ─── 保存配置 ─────────────────────────────────────────────────────────
  const onFinish = async (values: Record<string, unknown>) => {
    if (selectedGroupId == null) return false;
    const formatted = invokeFormatTemplateData(rawConfig, values);
    try {
      await saveServiceConfig(clusterId, instanceId, {
        roleGroupId: selectedGroupId,
        serviceConfig: formatted,
      });
      message.success('配置保存成功，请重启相关服务使配置生效');
      // 刷新版本列表（保存后会生成新版本）
      loadVersions(selectedGroupId);
      return true;
    } catch {
      return false;
    }
  };

  // ─── 菜单 items ────────────────────────────────────────────────────────
  const menuItems: MenuProps['items'] = roleGroups.map((g) => ({
    key: String(g.id),
    label: g.roleGroupName,
  }));

  return (
    <div style={{ display: 'flex', gap: 16, padding: '16px 0' }}>
      {/* 左侧角色组菜单 */}
      <div style={{ width: 200, flexShrink: 0 }}>
        <Menu
          mode="inline"
          selectedKeys={
            selectedGroupId != null ? [String(selectedGroupId)] : []
          }
          onClick={onMenuClick}
          items={menuItems}
          style={{ border: '1px solid #f0f0f0', borderRadius: 8 }}
        />
      </div>

      {/* 右侧内容区 */}
      <div style={{ flex: 1, minWidth: 0 }}>
        {/* 版本选择器 */}
        <div
          style={{
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <span style={{ flexShrink: 0 }}>配置版本：</span>
          <Select
            style={{ width: 160 }}
            value={selectedVersion}
            onChange={onVersionChange}
            options={versions.map((v) => ({ label: `版本 ${v}`, value: v }))}
            placeholder="请选择版本"
          />
        </div>

        {/* 配置表单 */}
        <Spin spinning={configLoading}>
          {templateData.length > 0 && (
            <ProForm
              key={formKey}
              form={formRef}
              onFinish={onFinish}
              submitter={{
                searchConfig: { submitText: '保存配置' },
                resetButtonProps: false,
              }}
              style={{ maxWidth: 800 }}
            >
              <ConfigForm templateData={templateData} />
            </ProForm>
          )}
          {!configLoading && templateData.length === 0 && (
            <div
              style={{ color: '#999', padding: '32px 0', textAlign: 'center' }}
            >
              暂无配置项
            </div>
          )}
        </Spin>
      </div>
    </div>
  );
};

// ── SettingTab：根据 archType 调度到对应实现 ───────────────────────────────

const SettingTab: React.FC<SettingTabProps> = ({ clusterId, instanceId }) => {
  const clusterCtx = useContext(ClusterContext);
  const isK8s = clusterCtx?.clusterInfo?.archType === 'k8s';

  if (isK8s) {
    return <HelmEditor clusterId={clusterId} instanceId={instanceId} />;
  }
  return <PhysicalSettingContent clusterId={clusterId} instanceId={instanceId} />;
};

export default SettingTab;
