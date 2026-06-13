import {
  ModalForm,
  ProFormText,
  ProFormUploadButton,
} from '@ant-design/pro-components';
import { history } from '@umijs/max';
import type { UploadRequestOption } from 'rc-upload/lib/interface';
import { message } from 'antd';
import React, { useContext } from 'react';
import ClusterContext from '@/context/ClusterContext';
import {
  deployManifest,
  uploadDeployFile,
  validateDeploymentFile,
} from '@/services/deploy';

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * 导入部署清单弹窗：上传 yaml 清单 + 解密密码 → 服务端校验 → 执行部署 → 跳 DAG 图。
 */
const UploadManifestModal: React.FC<Props> = ({ open, onClose }) => {
  // ClusterLayout 保证此组件仅在 clusterInfo 非空时渲染，context 必然非 null
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;

  /** 自定义上传：调 v2 接口，将响应存入 antd Upload 文件对象的 response 字段。 */
  const customUpload = async (options: UploadRequestOption) => {
    const { file, onSuccess, onError } = options;
    try {
      const res = await uploadDeployFile(clusterId, file as File);
      onSuccess?.(res);
    } catch (err) {
      onError?.(err as Error);
    }
  };

  const handleFinish = async (values: Record<string, unknown>) => {
    // 从 ProFormUploadButton 字段值读取已上传文件
    const uploadList = values.deployFileId as
      | Array<{ status: string; response?: { data?: { id?: number } } }>
      | undefined;
    const uploadFile = uploadList?.[0];

    if (!uploadFile || uploadFile.status !== 'done') {
      message.warning('请等待文件上传完成后再提交');
      return false;
    }

    const deployFileId = uploadFile.response?.data?.id;
    if (!deployFileId) {
      message.error('文件上传失败，请重新上传后重试');
      return false;
    }

    const contentDecodePasswd = (values.contentDecodePasswd as string) ?? '';

    // 1. 服务端校验清单（提前暴露错误，与旧版行为一致）
    const validateRes = await validateDeploymentFile(clusterId, {
      deployFileId,
      contentDecodePasswd,
    });
    const errors = validateRes?.data?.errors;
    if (errors?.length) {
      message.warning(`校验失败：${errors.join('，')}`);
      return false;
    }

    // 2. 执行部署
    const deployRes = await deployManifest(clusterId, {
      deployFileId,
      contentDecodePasswd,
    });

    const dagId = deployRes?.data?.dagId;
    if (!dagId) {
      message.error('部署失败：服务端未返回 DAG ID');
      return false;
    }

    onClose();
    // 跳转到 v2 DAG 全屏图（切片 6b 已迁移）
    history.push(`/cluster/${clusterId}/dag/${dagId}`);
    return true;
  };

  return (
    <ModalForm
      title="导入部署清单"
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
      onFinish={handleFinish}
      modalProps={{ destroyOnClose: true, okText: '开始部署', width: 520 }}
    >
      <ProFormUploadButton
        label="部署清单"
        name="deployFileId"
        max={1}
        listType="text"
        title="选择并上传部署清单"
        fieldProps={{ customRequest: customUpload }}
        rules={[
          {
            required: true,
            validator: (_, value) => {
              if (!value?.length) return Promise.reject('请上传部署清单');
              const f = value[0];
              if (f.status === 'uploading')
                return Promise.reject('正在上传中，请稍后重试');
              if (f.status !== 'done')
                return Promise.reject('上传失败，请重新上传');
              return Promise.resolve();
            },
          },
        ]}
      />
      <ProFormText
        label="配置文件密码"
        name="contentDecodePasswd"
        fieldProps={{
          placeholder: '如清单内容已加密请输入解密密码，否则留空',
        }}
      />
    </ModalForm>
  );
};

export default UploadManifestModal;
