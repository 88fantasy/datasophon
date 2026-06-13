import {
  ModalForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import React from 'react';
import { getServiceRoleGroupList, saveRoleGroup } from '@/services/service';

interface Props {
  open: boolean;
  clusterId: number;
  instanceId: number;
  onClose: () => void;
  onSuccess: () => void;
}

const AddRoleGroupModal: React.FC<Props> = ({
  open,
  clusterId,
  instanceId,
  onClose,
  onSuccess,
}) => {
  const { data: groupOptions = [] } = useRequest(
    () => getServiceRoleGroupList(clusterId, instanceId),
    {
      refreshDeps: [clusterId, instanceId],
      formatResult: (res) => {
        const list = (res as any)?.data ?? [];
        return Array.isArray(list)
          ? list.map((g: { id: number; roleGroupName: string }) => ({
              label: g.roleGroupName,
              value: g.id,
            }))
          : [];
      },
    },
  );

  return (
    <ModalForm
      title="添加角色组"
      open={open}
      width={440}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
      modalProps={{ destroyOnClose: true }}
      onFinish={async (values) => {
        try {
          await saveRoleGroup(clusterId, instanceId, {
            roleGroupName: values.roleGroupName as string,
            roleGroupId: values.roleGroupId as number | undefined,
          });
          message.success('角色组添加成功');
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
    >
      <ProFormText
        name="roleGroupName"
        label="角色组名称"
        rules={[{ required: true, message: '请输入角色组名称' }]}
        placeholder="请输入角色组名称"
      />
      <ProFormSelect
        name="roleGroupId"
        label="复制自（可选）"
        options={groupOptions}
        placeholder="选择已有角色组作为模板（可选）"
      />
    </ModalForm>
  );
};

export default AddRoleGroupModal;
