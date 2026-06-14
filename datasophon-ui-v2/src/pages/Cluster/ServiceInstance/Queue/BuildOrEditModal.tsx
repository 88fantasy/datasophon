import {
  ModalForm,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { message } from 'antd';
import React from 'react';
import { saveYarnQueue, updateYarnQueue } from '@/services/yarn';

const SCHEDULE_POLICY_OPTIONS = [
  { label: 'fair', value: 'fair' },
  { label: 'fifo', value: 'fifo' },
  { label: 'drf', value: 'drf' },
];

const QUEUE_NAME_PATTERN = /^(?!_)(?!.*?_$)[a-zA-Z0-9_]+$/;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  record?: DATASOPHON.YarnQueue | null;
  clusterId: number;
  onSuccess: () => void;
}

const BuildOrEditModal: React.FC<Props> = ({
  open,
  onOpenChange,
  record,
  clusterId,
  onSuccess,
}) => {
  const isEdit = !!record?.id;
  const title = isEdit ? '编辑队列' : '新建队列';

  const initialValues = record
    ? {
        ...record,
        allowPreemption: record.allowPreemption === 1,
      }
    : undefined;

  const onFinish = async (values: Record<string, any>) => {
    try {
      const base: DATASOPHON.SaveYarnQueueRequest = {
        queueName: values.queueName,
        minCore: values.minCore,
        minMem: values.minMem,
        maxCore: values.maxCore,
        maxMem: values.maxMem,
        appNum: values.appNum,
        schedulePolicy: values.schedulePolicy,
        weight: values.weight,
        amShare: String(values.amShare),
        allowPreemption: values.allowPreemption ? 1 : 2,
      };

      if (isEdit) {
        const updatePayload: DATASOPHON.UpdateYarnQueueRequest = {
          ...base,
          id: record?.id as number,
        };
        await updateYarnQueue(clusterId, updatePayload);
      } else {
        await saveYarnQueue(clusterId, base);
      }

      message.success(isEdit ? '修改成功' : '新建成功');
      onSuccess();
      return true;
    } catch {
      return false;
    }
  };

  return (
    <ModalForm
      title={title}
      open={open}
      onOpenChange={onOpenChange}
      onFinish={onFinish}
      initialValues={initialValues}
      layout="horizontal"
      labelCol={{ span: 8 }}
      width={520}
      modalProps={{ destroyOnClose: true }}
    >
      <ProFormText
        name="queueName"
        label="队列名称"
        disabled={isEdit}
        rules={[
          {
            required: true,
            validator: (_, value) => {
              if (!value) return Promise.reject('请填写名称');
              if (!QUEUE_NAME_PATTERN.test(value)) {
                return Promise.reject(
                  '名称只能是数字、字母、下划线且不能以下划线开头和结尾',
                );
              }
              return Promise.resolve();
            },
          },
        ]}
      />
      <ProFormDigit
        name="minCore"
        label="最小资源 (Core)"
        min={0}
        fieldProps={{ addonAfter: 'Core' }}
        rules={[{ required: true, message: '请输入最小 Core 数' }]}
      />
      <ProFormDigit
        name="minMem"
        label="最小资源 (内存)"
        min={0}
        fieldProps={{ addonAfter: 'GB' }}
        rules={[{ required: true, message: '请输入最小内存' }]}
      />
      <ProFormDigit
        name="maxCore"
        label="最大资源 (Core)"
        min={0}
        fieldProps={{ addonAfter: 'Core' }}
        rules={[{ required: true, message: '请输入最大 Core 数' }]}
      />
      <ProFormDigit
        name="maxMem"
        label="最大资源 (内存)"
        min={0}
        fieldProps={{ addonAfter: 'GB' }}
        rules={[{ required: true, message: '请输入最大内存' }]}
      />
      <ProFormDigit
        name="appNum"
        label="最多同时运行应用数"
        min={1}
        rules={[{ required: true, message: '请输入应用数' }]}
      />
      <ProFormSelect
        name="schedulePolicy"
        label="资源分配策略"
        options={SCHEDULE_POLICY_OPTIONS}
        rules={[{ required: true, message: '请选择分配策略' }]}
      />
      <ProFormDigit
        name="weight"
        label="权重"
        min={0}
        rules={[{ required: true, message: '请输入权重' }]}
      />
      <ProFormText
        name="amShare"
        label="AM 占用最大比例"
        rules={[
          {
            required: true,
            validator: (_, value) => {
              if (!value) return Promise.reject('请输入正数');
              const num = Number(value);
              if (Number.isNaN(num) || num <= 0)
                return Promise.reject('请输入正数');
              return Promise.resolve();
            },
          },
        ]}
      />
      <ProFormSwitch name="allowPreemption" label="是否允许队列抢占资源" />
    </ModalForm>
  );
};

export default BuildOrEditModal;
