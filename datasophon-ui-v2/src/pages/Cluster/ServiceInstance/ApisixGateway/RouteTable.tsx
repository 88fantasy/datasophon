import {
  type ProColumns,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import React from 'react';
import type { ApisixGatewayDoc, ApisixRoute } from './gatewayYaml';
import { ResourceCrudTable } from './ResourceCrudTable';

interface RouteTableProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const columns: ProColumns<ApisixRoute>[] = [
  { title: 'uri', dataIndex: 'uri' },
  { title: 'upstream_id', dataIndex: 'upstream_id', width: 120 },
  {
    title: 'plugins',
    dataIndex: 'plugins',
    render: (_, record) =>
      record.plugins ? Object.keys(record.plugins).join(', ') : '-',
  },
];

const RouteTable: React.FC<RouteTableProps> = ({ doc, onChange }) => {
  const routes = doc.routes ?? [];
  const upstreamOptions = (doc.upstreams ?? []).map((u) => ({
    label: String(u.id),
    value: u.id,
  }));

  return (
    <ResourceCrudTable<ApisixRoute>
      resourceLabel="Route"
      items={routes}
      onChange={(next) => onChange({ ...doc, routes: next })}
      columns={columns}
      getInitialValues={(editRecord) =>
        editRecord
          ? {
              id: editRecord.id,
              uri: editRecord.uri,
              upstream_id: editRecord.upstream_id,
              pluginsJson: editRecord.plugins
                ? JSON.stringify(editRecord.plugins, null, 2)
                : '',
            }
          : {}
      }
      parseValues={(values, editRecord) => {
        let plugins: Record<string, unknown> | undefined;
        if (values.pluginsJson?.trim()) {
          try {
            plugins = JSON.parse(values.pluginsJson);
          } catch {
            return 'plugins 不是合法的 JSON';
          }
        }
        return {
          ...(editRecord ?? {}),
          id: values.id,
          uri: values.uri,
          upstream_id: values.upstream_id,
          plugins,
        };
      }}
      formFields={
        <>
          <ProFormText name="uri" label="uri" rules={[{ required: true }]} />
          <ProFormSelect
            name="upstream_id"
            label="upstream_id"
            options={upstreamOptions}
            rules={[{ required: true, message: '请选择 upstream' }]}
          />
          <ProFormTextArea
            name="pluginsJson"
            label="plugins（JSON，可留空）"
            fieldProps={{ rows: 6 }}
          />
        </>
      }
    />
  );
};

export default RouteTable;
