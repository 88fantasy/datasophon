import { ModalForm, ProFormSelect } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import React from 'react';
import { assignRack, getRackList } from '@/services/datasophon/host';

interface Props {
  clusterId: number;
  hostIds: number[];
  trigger: React.ReactElement;
  onSuccess: () => void;
}

const AssignRackModal: React.FC<Props> = ({
  clusterId,
  hostIds,
  trigger,
  onSuccess,
}) => {
  const { data: rackOptions = [] } = useRequest(() => getRackList(clusterId), {
    formatResult: (res) => {
      const list = res.data ?? [];
      return Array.isArray(list)
        ? list.map((r) => ({ label: String(r), value: String(r) }))
        : [];
    },
  });

  return (
    <ModalForm
      title="分配机架"
      trigger={trigger}
      width={400}
      onFinish={async (values) => {
        try {
          await assignRack(clusterId, { rack: values.rack, hostIds });
          message.success('机架分配成功');
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
    >
      <ProFormSelect
        name="rack"
        label="机架名称"
        rules={[{ required: true, message: '请选择机架' }]}
        options={rackOptions}
        placeholder="请选择机架"
      />
    </ModalForm>
  );
};

export default AssignRackModal;
