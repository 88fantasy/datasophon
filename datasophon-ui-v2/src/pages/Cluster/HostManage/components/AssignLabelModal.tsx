import { ModalForm, ProFormSelect } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import React from 'react';
import { assignNodeLabel, listNodeLabels } from '@/services/label';

interface Props {
  clusterId: number;
  hostIds: number[];
  trigger: React.ReactElement;
  onSuccess: () => void;
}

const AssignLabelModal: React.FC<Props> = ({
  clusterId,
  hostIds,
  trigger,
  onSuccess,
}) => {
  const { data: labelOptions = [] } = useRequest(
    () => listNodeLabels(clusterId),
    {
      formatResult: (res) => {
        const list = res.data ?? [];
        return Array.isArray(list)
          ? list.map((l) => ({ label: l.nodeLabel, value: l.id }))
          : [];
      },
    },
  );

  return (
    <ModalForm
      title="分配标签"
      trigger={trigger}
      width={400}
      onFinish={async (values) => {
        try {
          await assignNodeLabel(clusterId, values.nodeLabelId, hostIds);
          message.success('标签分配成功');
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
    >
      <ProFormSelect
        name="nodeLabelId"
        label="节点标签"
        rules={[{ required: true, message: '请选择标签' }]}
        options={labelOptions}
        placeholder="请选择标签"
      />
    </ModalForm>
  );
};

export default AssignLabelModal;
