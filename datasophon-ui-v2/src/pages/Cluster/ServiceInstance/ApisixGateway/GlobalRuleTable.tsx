import { type ProColumns, ProFormTextArea } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import { Tag } from 'antd';
import React from 'react';
import {
  type ApisixGatewayDoc,
  type ApisixGlobalRule,
  isBuiltinGlobalRule,
  removeGlobalRule,
} from './gatewayYaml';
import { ResourceCrudTable } from './ResourceCrudTable';

interface GlobalRuleTableProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const GlobalRuleTable: React.FC<GlobalRuleTableProps> = ({ doc, onChange }) => {
  const intl = useIntl();
  const rules = doc.global_rules ?? [];

  const columns: ProColumns<ApisixGlobalRule>[] = [
    {
      title: 'plugins',
      dataIndex: 'plugins',
      render: (_, record) =>
        record.plugins ? Object.keys(record.plugins).join(', ') : '-',
    },
    {
      title: '',
      dataIndex: 'builtin',
      width: 100,
      render: (_, record) =>
        isBuiltinGlobalRule(record) ? (
          <Tag color="blue">
            {intl.formatMessage({
              id: 'pages.apisixGateway.globalRule.builtin',
            })}
          </Tag>
        ) : null,
    },
  ];

  return (
    <ResourceCrudTable<ApisixGlobalRule>
      resourceLabel="Global Rule"
      items={rules}
      onChange={(next) => onChange({ ...doc, global_rules: next })}
      columns={columns}
      canModify={(record) => !isBuiltinGlobalRule(record)}
      deleteItem={(items, record) => removeGlobalRule(items, record.id)}
      getInitialValues={(editRecord) =>
        editRecord
          ? {
              id: editRecord.id,
              pluginsJson: JSON.stringify(editRecord.plugins ?? {}, null, 2),
            }
          : { pluginsJson: '{\n\n}' }
      }
      parseValues={(values, editRecord) => {
        let plugins: Record<string, unknown>;
        try {
          plugins = JSON.parse(values.pluginsJson || '{}');
        } catch {
          return 'plugins 不是合法的 JSON';
        }
        return { ...(editRecord ?? {}), id: values.id, plugins };
      }}
      formFields={
        <ProFormTextArea
          name="pluginsJson"
          label="plugins（JSON）"
          fieldProps={{ rows: 6 }}
          rules={[{ required: true, message: '请输入 plugins' }]}
        />
      }
    />
  );
};

export default GlobalRuleTable;
