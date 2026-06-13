import {
  ModalForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import React from 'react';
import {
  listAlertCategories,
  saveAlertGroup,
} from '@/services/alarm';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  clusterId: number;
  onSuccess: () => void;
}

const GroupModal: React.FC<Props> = ({
  open,
  onOpenChange,
  clusterId,
  onSuccess,
}) => {
  const { data: categoryOptions = [] } = useRequest(
    () => listAlertCategories(clusterId),
    {
      refreshDeps: [clusterId],
      formatResult: (res) => {
        const list = res?.data ?? [];
        return Array.isArray(list)
          ? list.map((item: DATASOPHON.AlertCategoryResponse) => ({
              label: item.serviceName,
              value: item.serviceName,
            }))
          : [];
      },
    },
  );

  return (
    <ModalForm
      title="新建告警组"
      open={open}
      onOpenChange={onOpenChange}
      width={480}
      layout="horizontal"
      labelCol={{ span: 6 }}
      modalProps={{ destroyOnClose: true }}
      onFinish={async (values) => {
        try {
          await saveAlertGroup(clusterId, {
            alertGroupName: values.alertGroupName,
            alertGroupCategory: values.alertGroupCategory,
          });
          message.success('告警组创建成功');
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
    >
      <ProFormText
        name="alertGroupName"
        label="告警组名称"
        rules={[{ required: true, message: '请输入告警组名称' }]}
      />
      <ProFormSelect
        name="alertGroupCategory"
        label="告警组类别"
        options={categoryOptions}
        rules={[{ required: true, message: '请选择告警组类别' }]}
        placeholder="请选择关联服务"
      />
    </ModalForm>
  );
};

export default GroupModal;
