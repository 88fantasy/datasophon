import type { ProFormInstance } from '@ant-design/pro-components';
import { StepsForm } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Modal, message } from 'antd';
import React, { useContext, useRef, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import { invokeFormatTemplateData } from '@/pages/Cluster/ServiceInstance/Setting/configTransform';
import {
  checkServiceDependency,
  installServices,
  saveRoleHostMapping,
  saveServiceConfig,
} from '@/services/addService';
import { validateDeploymentFile } from '@/services/deploy';
import StepConfig from './StepConfig';
import StepInstall from './StepInstall';
import StepManifest from './StepManifest';
import StepRoleAssign from './StepRoleAssign';
import StepSelectService from './StepSelectService';

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * 添加服务向导（物理集群）：导入清单 → 选服务 → 分配 Master 角色 →
 * 分配 Worker 角色 → 服务配置 → 安装并启动（跳 DAG 全屏图）。
 */
const AddServiceModal: React.FC<Props> = ({ open, onClose }) => {
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;

  const formMapRef = useRef<
    React.MutableRefObject<ProFormInstance | undefined>[]
  >([]);
  const [currentStep, setCurrentStep] = useState(0);

  // ── 跨步骤数据 ─────────────────────────────────────────────
  const [manifest, setManifest] = useState<DATASOPHON.ManifestContext | null>(
    null,
  );
  const [services, setServices] = useState<DATASOPHON.FrameService[]>([]);
  const masterRolesRef = useRef<DATASOPHON.FrameServiceRole[]>([]);
  const workerRolesRef = useRef<DATASOPHON.FrameServiceRole[]>([]);
  const rawConfigMapRef = useRef<Record<string, DATASOPHON.ConfigField[]>>({});

  // ── 步骤 1：校验清单并记录上下文 ────────────────────────────
  const handleManifestFinish = async (values: Record<string, unknown>) => {
    const uploadList = values.deployFile as
      | Array<{ status: string; response?: { data?: { id?: number } } }>
      | undefined;
    const uploadFile = uploadList?.[0];
    if (uploadFile?.status !== 'done') {
      message.warning('请等待文件上传完成后再继续');
      return false;
    }
    const deployFileId = uploadFile.response?.data?.id;
    if (!deployFileId) {
      message.error('文件上传失败，请重新上传后重试');
      return false;
    }
    const contentDecodePasswd = (values.contentDecodePasswd as string) ?? '';

    try {
      const res = await validateDeploymentFile(clusterId, {
        deployFileId,
        contentDecodePasswd,
      });
      const errors = res?.data?.errors;
      if (errors?.length) {
        message.warning(`校验失败：${errors.join('，')}`);
        return false;
      }
    } catch {
      return false;
    }
    setManifest({ deployFileId, contentDecodePasswd });
    return true;
  };

  // ── 步骤 2：依赖校验 ────────────────────────────────────────
  const handleSelectFinish = async () => {
    if (services.length === 0) {
      message.warning('请至少选择一个服务');
      return false;
    }
    try {
      await checkServiceDependency(
        clusterId,
        services.map((s) => s.id),
      );
      return true;
    } catch {
      return false;
    }
  };

  // ── 步骤 3/4：保存角色-主机映射 ─────────────────────────────
  const buildRoleFinish =
    (rolesRef: React.MutableRefObject<DATASOPHON.FrameServiceRole[]>) =>
    async (values: Record<string, unknown>) => {
      const roles = rolesRef.current;
      if (roles.length === 0) return true; // 无角色直接放行
      const mappings: DATASOPHON.RoleHostMapping[] = roles.map((role) => {
        const v = values[role.serviceRoleName];
        const hosts = Array.isArray(v)
          ? (v as string[])
          : v
            ? [v as string]
            : [];
        return { serviceRole: role.serviceRoleName, hosts };
      });
      try {
        await saveRoleHostMapping(clusterId, mappings);
        return true;
      } catch {
        return false;
      }
    };

  // ── 步骤 5：逐服务保存配置 ──────────────────────────────────
  const handleConfigFinish = async (values: Record<string, unknown>) => {
    try {
      for (const svc of services) {
        const raw = rawConfigMapRef.current[svc.serviceName];
        if (!raw?.length) continue;
        const svcValues = (values[svc.serviceName] ?? {}) as Record<
          string,
          unknown
        >;
        const formatted = invokeFormatTemplateData(raw, svcValues);
        await saveServiceConfig(clusterId, {
          serviceName: svc.serviceName,
          serviceConfig: formatted,
          roleGroupId: -1,
        });
      }
      return true;
    } catch {
      return false;
    }
  };

  // ── 步骤 6：生成安装命令并执行，跳 DAG 图 ───────────────────
  const handleInstallFinish = async () => {
    try {
      const res = await installServices(
        clusterId,
        services.map((s) => s.serviceName),
      );
      const dagId = res?.data?.dagId;
      if (!dagId) {
        message.error('安装启动失败：服务端未返回 DAG ID');
        return false;
      }
      onClose();
      history.push(`/cluster/${clusterId}/dag/${dagId}`);
      return true;
    } catch {
      return false;
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      title="添加服务"
      width={960}
      destroyOnHidden
      afterClose={() => {
        setCurrentStep(0);
        setManifest(null);
        setServices([]);
        masterRolesRef.current = [];
        workerRolesRef.current = [];
        rawConfigMapRef.current = {};
      }}
    >
      <StepsForm
        formMapRef={formMapRef}
        current={currentStep}
        onCurrentChange={setCurrentStep}
        stepsProps={{ size: 'small' }}
        submitter={{
          render: (props) => {
            const { step, onSubmit, onPre } = props;
            return (
              <div
                style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}
              >
                {step > 0 && <Button onClick={onPre}>上一步</Button>}
                <Button type="primary" onClick={onSubmit}>
                  {step === 5 ? '开始安装' : '下一步'}
                </Button>
              </div>
            );
          },
        }}
      >
        <StepsForm.StepForm
          name="manifest"
          title="导入清单"
          onFinish={handleManifestFinish}
        >
          <StepManifest clusterId={clusterId} />
        </StepsForm.StepForm>

        <StepsForm.StepForm
          name="select"
          title="选择服务"
          onFinish={handleSelectFinish}
        >
          <StepSelectService
            clusterId={clusterId}
            manifest={manifest}
            active={currentStep === 1}
            value={services}
            onChange={setServices}
          />
        </StepsForm.StepForm>

        <StepsForm.StepForm
          name="master"
          title="分配 Master 角色"
          onFinish={buildRoleFinish(masterRolesRef)}
        >
          <StepRoleAssign
            clusterId={clusterId}
            manifest={manifest}
            services={services}
            active={currentStep === 2}
            roleType="master"
            rolesRef={masterRolesRef}
          />
        </StepsForm.StepForm>

        <StepsForm.StepForm
          name="worker"
          title="分配 Worker 角色"
          onFinish={buildRoleFinish(workerRolesRef)}
        >
          <StepRoleAssign
            clusterId={clusterId}
            manifest={manifest}
            services={services}
            active={currentStep === 3}
            roleType="worker"
            rolesRef={workerRolesRef}
          />
        </StepsForm.StepForm>

        <StepsForm.StepForm
          name="config"
          title="服务配置"
          onFinish={handleConfigFinish}
        >
          <StepConfig
            clusterId={clusterId}
            services={services}
            active={currentStep === 4}
            rawConfigMapRef={rawConfigMapRef}
          />
        </StepsForm.StepForm>

        <StepsForm.StepForm
          name="install"
          title="安装并启动"
          onFinish={handleInstallFinish}
        >
          <StepInstall services={services} />
        </StepsForm.StepForm>
      </StepsForm>
    </Modal>
  );
};

export default AddServiceModal;
