import { ModalForm, ProFormSelect } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import React from 'react';
import {
  bindRoleGroup,
  getServiceRoleGroupList,
} from '@/services/datasophon/service';

interface Props {
  open: boolean;
  clusterId: number;
  instanceId: number;
  selectedIds: number[];
  onClose: () => void;
  onSuccess: () => void;
}

const AssignRoleGroupModal: React.FC<Props> = ({
  open,
  clusterId,
  instanceId,
  selectedIds,
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
      title="分配角色组"
      open={open}
      width={400}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
      modalProps={{ destroyOnClose: true }}
      onFinish={async (values) => {
        try {
          await bindRoleGroup(clusterId, instanceId, {
            roleInstanceIds: selectedIds.join(','),
            roleGroupId: values.roleGroupId as number,
          });
          message.success('角色组分配成功');
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
    >
      <ProFormSelect
        name="roleGroupId"
        label="角色组"
        options={groupOptions}
        rules={[{ required: true, message: '请选择角色组' }]}
        placeholder="请选择角色组"
      />
    </ModalForm>
  );
};

export default AssignRoleGroupModal;
