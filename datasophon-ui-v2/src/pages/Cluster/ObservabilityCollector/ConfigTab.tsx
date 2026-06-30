import {
  ProForm,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Alert, message, Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import {
  type CollectorConfigField,
  getCollectorConfig,
  getCollectorMonitor,
  pushCollectorConfig,
} from './service';

interface ConfigTabProps {
  clusterId: number;
}

const ConfigTab: React.FC<ConfigTabProps> = ({ clusterId }) => {
  const intl = useIntl();
  const [fields, setFields] = useState<CollectorConfigField[]>([]);
  const [hostnames, setHostnames] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.allSettled([
      getCollectorConfig(clusterId),
      getCollectorMonitor(clusterId),
    ])
      .then(([configResult, monitorResult]) => {
        if (cancelled) return;
        if (configResult.status === 'fulfilled') {
          setFields(configResult.value.data ?? []);
        }
        if (monitorResult.status === 'fulfilled') {
          setHostnames(
            (monitorResult.value.data ?? []).map((node) => node.hostname),
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [clusterId]);

  const visibleFields = useMemo(
    () =>
      fields.filter(
        (field) =>
          field.name === 'rawYaml' ||
          (!field.hidden && field.configurableInWizard !== false),
      ),
    [fields],
  );

  const initialValues = useMemo(() => {
    const values: Record<string, unknown> = {};
    for (const field of fields) {
      const value = field.value ?? field.defaultValue ?? '';
      values[field.name] =
        field.type === 'input-number' && value !== '' ? Number(value) : value;
    }
    if (hostnames.length === 1) values.hostname = hostnames[0];
    return values;
  }, [fields, hostnames]);

  const onFinish = async (values: Record<string, unknown>) => {
    const hostname = String(values.hostname ?? '');
    const params: Record<string, string> = {};
    for (const field of fields) {
      const value = values[field.name] ?? field.value ?? field.defaultValue;
      params[field.name] = value == null ? '' : String(value);
    }
    try {
      await pushCollectorConfig(clusterId, hostname, params);
      message.success(
        intl.formatMessage({
          id: 'pages.observabilityCollector.pushSuccess',
          defaultMessage: 'Collector configuration pushed',
        }),
      );
      return true;
    } catch {
      return false;
    }
  };

  const renderField = (field: CollectorConfigField) => {
    const common = {
      key: field.name,
      name: field.name,
      label: field.label || field.name,
      tooltip: field.description,
      rules: field.required
        ? [
            {
              required: true,
              message: intl.formatMessage({
                id: 'pages.observabilityCollector.required',
                defaultMessage: 'This field is required',
              }),
            },
          ]
        : undefined,
    };
    if (field.type === 'select') {
      return (
        <ProFormSelect
          {...common}
          options={(field.selectValue ?? []).map((value) => ({
            label: value,
            value,
          }))}
        />
      );
    }
    if (field.type === 'input-number') {
      return (
        <ProFormDigit
          {...common}
          fieldProps={{ min: field.minValue, max: field.maxValue }}
        />
      );
    }
    if (field.type === 'textarea' || field.name === 'rawYaml') {
      return <ProFormTextArea {...common} fieldProps={{ rows: 14 }} />;
    }
    return <ProFormText {...common} />;
  };

  return (
    <Spin spinning={loading}>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        title={intl.formatMessage({
          id: 'pages.observabilityCollector.rawYamlWarning',
          defaultMessage:
            'When raw YAML is non-empty it replaces generated otelcol.yaml. Reference secrets through environment variables.',
        })}
      />
      {!loading && (
        <ProForm
          key={`${clusterId}-${fields.length}-${hostnames.join(',')}`}
          initialValues={initialValues}
          onFinish={onFinish}
          submitter={{
            searchConfig: {
              submitText: intl.formatMessage({
                id: 'pages.observabilityCollector.push',
                defaultMessage: 'Push and restart',
              }),
            },
            resetButtonProps: false,
          }}
          style={{ maxWidth: 880 }}
        >
          <ProFormSelect
            name="hostname"
            label={intl.formatMessage({
              id: 'pages.observabilityCollector.hostname',
              defaultMessage: 'Collector node',
            })}
            options={hostnames.map((hostname) => ({
              label: hostname,
              value: hostname,
            }))}
            rules={[{ required: true }]}
          />
          {visibleFields.map(renderField)}
        </ProForm>
      )}
    </Spin>
  );
};

export default ConfigTab;
