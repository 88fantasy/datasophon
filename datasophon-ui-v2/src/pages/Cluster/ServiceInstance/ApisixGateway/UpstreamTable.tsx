import {
  type ProColumns,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import React from 'react';
import type { ApisixGatewayDoc, ApisixUpstream } from './gatewayYaml';
import { ResourceCrudTable } from './ResourceCrudTable';

interface UpstreamTableProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const columns: ProColumns<ApisixUpstream>[] = [
  { title: 'type', dataIndex: 'type', width: 120 },
  {
    title: 'nodes',
    dataIndex: 'nodes',
    render: (_, record) => JSON.stringify(record.nodes ?? {}),
  },
];

const UpstreamTable: React.FC<UpstreamTableProps> = ({ doc, onChange }) => {
  const upstreams = doc.upstreams ?? [];

  return (
    <ResourceCrudTable<ApisixUpstream>
      resourceLabel="Upstream"
      items={upstreams}
      onChange={(next) => onChange({ ...doc, upstreams: next })}
      columns={columns}
      getInitialValues={(editRecord) =>
        editRecord
          ? {
              id: editRecord.id,
              type: editRecord.type ?? 'roundrobin',
              nodesJson: JSON.stringify(editRecord.nodes ?? {}, null, 2),
            }
          : { type: 'roundrobin', nodesJson: '{\n  "127.0.0.1:8080": 1\n}' }
      }
      parseValues={(values, editRecord) => {
        let nodes: Record<string, number>;
        try {
          nodes = JSON.parse(values.nodesJson || '{}');
        } catch {
          return 'nodes 不是合法的 JSON';
        }
        return {
          ...(editRecord ?? {}),
          id: values.id,
          type: values.type,
          nodes,
        };
      }}
      formFields={
        <>
          <ProFormText name="type" label="type" rules={[{ required: true }]} />
          <ProFormTextArea
            name="nodesJson"
            label="nodes（JSON，host:port -> 权重）"
            fieldProps={{ rows: 6 }}
            rules={[{ required: true, message: '请输入 nodes' }]}
          />
        </>
      }
    />
  );
};

export default UpstreamTable;
