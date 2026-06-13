import {
  ModalForm,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Form, message } from 'antd';
import React from 'react';
import {
  listAlertGroups,
  listQuotaRoles,
  saveAlertQuota,
  updateAlertQuota,
} from '@/services/alarm';

const COMPARE_OPTIONS = [
  { label: '!=', value: '!=' },
  { label: '>', value: '>' },
  { label: '<', value: '<' },
];

const LEVEL_OPTIONS = [
  { label: '警告', value: 'warning' },
  { label: '异常', value: 'exception' },
];

const TACTIC_OPTIONS = [
  { label: '单次', value: 1 },
  { label: '持续', value: 2 },
];

const NOTICE_OPTIONS = [{ label: '数据开发组', value: 1 }];

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  record?: DATASOPHON.AlertQuotaResponse | null;
  clusterId: number;
  defaultGroupId?: number;
  onSuccess: () => void;
}

const MetricModal: React.FC<Props> = ({
  open,
  onOpenChange,
  record,
  clusterId,
  defaultGroupId,
  onSuccess,
}) => {
  const isEdit = !!record?.id;
  const [form] = Form.useForm();

  const { data: groupOptions = [] } = useRequest(
    () => listAlertGroups(clusterId, { page: 1, pageSize: 1000 }),
    {
      refreshDeps: [clusterId],
      formatResult: (res) => {
        const list = res?.data?.totalList ?? [];
        return Array.isArray(list)
          ? list.map((g: DATASOPHON.AlertGroupResponse) => ({
              label: g.alertGroupName,
              value: g.id,
            }))
          : [];
      },
    },
  );

  const initialValues = record
    ? { ...record }
    : defaultGroupId
      ? { alertGroupId: defaultGroupId }
      : undefined;

  return (
    <ModalForm
      form={form}
      title={isEdit ? '编辑告警指标' : '新建告警指标'}
      open={open}
      onOpenChange={onOpenChange}
      width={560}
      layout="horizontal"
      labelCol={{ span: 8 }}
      modalProps={{ destroyOnClose: true }}
      initialValues={initialValues}
      onValuesChange={(changed) => {
        if ('alertGroupId' in changed) {
          form.setFieldValue('serviceRoleName', undefined);
        }
      }}
      onFinish={async (values) => {
        try {
          if (isEdit) {
            await updateAlertQuota(clusterId, { ...values, id: record!.id! });
          } else {
            await saveAlertQuota(clusterId, values);
          }
          message.success(isEdit ? '修改成功' : '新建成功');
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
    >
      <ProFormText
        name="alertQuotaName"
        label="告警指标名称"
        rules={[{ required: true, message: '请输入指标名称' }]}
      />
      <ProFormText
        name="alertExpr"
        label="指标表达式"
        rules={[{ required: true, message: '请输入指标表达式' }]}
      />
      <ProFormSelect
        name="compareMethod"
        label="比较方式"
        options={COMPARE_OPTIONS}
        rules={[{ required: true, message: '请选择比较方式' }]}
      />
      <ProFormText
        name="alertThreshold"
        label="告警阀值"
        rules={[{ required: true, message: '请输入阀值' }]}
      />
      <ProFormSelect
        name="alertLevel"
        label="告警级别"
        options={LEVEL_OPTIONS}
        rules={[{ required: true, message: '请选择告警级别' }]}
      />
      <ProFormSelect
        name="alertGroupId"
        label="告警组"
        options={groupOptions}
        rules={[{ required: true, message: '请选择告警组' }]}
        placeholder="请选择告警组"
      />
      <ProFormSelect
        name="serviceRoleName"
        label="绑定角色"
        dependencies={['alertGroupId']}
        rules={[{ required: true, message: '请选择绑定角色' }]}
        placeholder="请先选择告警组"
        request={async (params: { alertGroupId?: number }) => {
          if (!params.alertGroupId) return [];
          const res = await listQuotaRoles(clusterId, params.alertGroupId);
          const list = res?.data ?? [];
          return Array.isArray(list)
            ? list.map((r: { serviceRoleName: string }) => ({
                label: r.serviceRoleName,
                value: r.serviceRoleName,
              }))
            : [];
        }}
      />
      <ProFormSelect
        name="noticeGroupId"
        label="通知组"
        options={NOTICE_OPTIONS}
        rules={[{ required: true, message: '请选择通知组' }]}
      />
      <ProFormRadio.Group
        name="alertTactic"
        label="告警策略"
        options={TACTIC_OPTIONS}
        rules={[{ required: true, message: '请选择告警策略' }]}
      />
      <ProFormText
        name="intervalDuration"
        label="间隔时长(分钟)"
        rules={[{ required: true, message: '请输入间隔时长' }]}
      />
      <ProFormText
        name="triggerDuration"
        label="触发时长(秒)"
        rules={[{ required: true, message: '请输入触发时长' }]}
      />
      <ProFormTextArea
        name="alertAdvice"
        label="告警建议"
        rules={[{ required: true, message: '请输入告警建议' }]}
      />
    </ModalForm>
  );
};

export default MetricModal;
