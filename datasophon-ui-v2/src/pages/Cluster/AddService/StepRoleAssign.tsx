import { ProFormSelect } from '@ant-design/pro-components';
import { Empty, Spin } from 'antd';
import React, { useEffect, useState } from 'react';
import {
  listManagedHosts,
  listMasterRoles,
  listNonMasterRoles,
} from '@/services/datasophon/addService';

interface Props {
  clusterId: number;
  manifest: DATASOPHON.ManifestContext | null;
  services: DATASOPHON.FrameService[];
  active: boolean;
  /** master：必填单/多选；worker：非 Master 角色（Worker/Client），可留空 */
  roleType: 'master' | 'worker';
  /** 容器持有的角色列表 ref，提交时据此构建 role-host 映射 */
  rolesRef: React.MutableRefObject<DATASOPHON.FrameServiceRole[]>;
}

/** 步骤 3/4 共用：为每个服务角色分配主机（cardinality=1 单选，否则多选）。 */
const StepRoleAssign: React.FC<Props> = ({
  clusterId,
  manifest,
  services,
  active,
  roleType,
  rolesRef,
}) => {
  const [roles, setRoles] = useState<DATASOPHON.FrameServiceRole[]>([]);
  const [hostOptions, setHostOptions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!active || !manifest || services.length === 0) return;
    let cancelled = false;
    setLoading(true);

    const req = { ...manifest, serviceIds: services.map((s) => s.id) };
    const fetchRoles =
      roleType === 'master'
        ? listMasterRoles(clusterId, req)
        : listNonMasterRoles(clusterId, req);

    Promise.all([fetchRoles, listManagedHosts(clusterId)])
      .then(([roleRes, hostRes]) => {
        if (cancelled) return;
        const roleList = roleRes.data ?? [];
        setRoles(roleList);
        rolesRef.current = roleList;
        setHostOptions((hostRes.data ?? []).map((h) => h.hostname));
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
  }, [active, clusterId, manifest, services, roleType, rolesRef]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 0' }}>
        <Spin />
      </div>
    );
  }

  if (roles.length === 0) {
    return (
      <Empty
        description={
          roleType === 'master'
            ? '所选服务无 Master 角色'
            : '所选服务无 Worker 角色'
        }
        style={{ padding: '32px 0' }}
      />
    );
  }

  return (
    <div style={{ maxHeight: '45vh', overflow: 'auto', paddingRight: 8 }}>
      {roles.map((role) => {
        const isSingle = role.cardinality === '1';
        return (
          <ProFormSelect
            key={role.serviceRoleName}
            name={role.serviceRoleName}
            label={role.serviceRoleName}
            mode={isSingle ? undefined : 'multiple'}
            options={hostOptions.map((h) => ({ label: h, value: h }))}
            initialValue={
              role.hosts?.length
                ? isSingle
                  ? role.hosts[0]
                  : role.hosts
                : undefined
            }
            rules={[
              {
                required: roleType === 'master',
                message: `请为 ${role.serviceRoleName} 分配主机`,
              },
            ]}
          />
        );
      })}
    </div>
  );
};

export default StepRoleAssign;
