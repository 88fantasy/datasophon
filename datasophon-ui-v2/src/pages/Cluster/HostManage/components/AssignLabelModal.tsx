import { ModalForm, ProFormSelect } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import {
  assignNodeLabel,
  listNodeLabels,
  saveNodeLabel,
} from '@/services/label';
import { getApiFailureMessage } from '@/utils/apiResponse';
import { BUILTIN_HOST_LABELS } from '@/utils/hostCategories';

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
  const { message } = App.useApp();
  const { data: labels = [], refresh } = useRequest(async () => {
    const res = await listNodeLabels(clusterId);
    const failure = getApiFailureMessage(res, '标签加载失败');
    if (failure) throw new Error(failure);
    return { data: res.data ?? [] };
  });
  const labelOptions = Array.from(
    new Set([
      ...BUILTIN_HOST_LABELS.map((label) => label.value),
      ...labels.map((label: DATASOPHON.NodeLabelResponse) => label.nodeLabel),
    ]),
  ).map((value) => {
    const builtin = BUILTIN_HOST_LABELS.find((label) => label.value === value);
    return { label: builtin ? `${builtin.label}（${value}）` : value, value };
  });

  return (
    <ModalForm
      title="分配标签"
      trigger={trigger}
      width={400}
      onOpenChange={(open) => {
        if (open) refresh();
      }}
      onFinish={async (values) => {
        try {
          let res = await listNodeLabels(clusterId);
          let failure = getApiFailureMessage(res, '标签加载失败');
          if (failure) throw new Error(failure);
          let label = res.data.find(
            (item) => item.nodeLabel === values.nodeLabel,
          );
          if (
            !label &&
            BUILTIN_HOST_LABELS.some((item) => item.value === values.nodeLabel)
          ) {
            const saved = await saveNodeLabel(clusterId, values.nodeLabel);
            failure = getApiFailureMessage(saved, '标签创建失败');
            if (failure) throw new Error(failure);
            res = await listNodeLabels(clusterId);
            failure = getApiFailureMessage(res, '标签加载失败');
            if (failure) throw new Error(failure);
            label = res.data.find(
              (item) => item.nodeLabel === values.nodeLabel,
            );
          }
          if (!label) throw new Error('标签不存在，请重新选择');
          const assigned = await assignNodeLabel(clusterId, label.id, hostIds);
          failure = getApiFailureMessage(assigned, '标签分配失败');
          if (failure) throw new Error(failure);
          message.success('标签分配成功');
          onSuccess();
          return true;
        } catch (error) {
          message.error(
            error instanceof Error ? error.message : '标签分配失败',
          );
          return false;
        }
      }}
    >
      <ProFormSelect
        name="nodeLabel"
        label="节点标签"
        rules={[{ required: true, message: '请选择标签' }]}
        options={labelOptions}
        placeholder="请选择标签"
        extra="内置类别用于拓扑子集群归属；未配置或其他标签归入其他子集群。安装 YARN 的集群会同步替换节点的 YARN 分区标签。"
      />
    </ModalForm>
  );
};

export default AssignLabelModal;
