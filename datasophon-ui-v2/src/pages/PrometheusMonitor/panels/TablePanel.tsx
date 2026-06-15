import { Card, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { FC } from 'react';
import type { PrometheusTableRow } from '../mock/prometheusMockData';

const { Text } = Typography;

const columns: ColumnsType<PrometheusTableRow> = [
  {
    title: 'Instance',
    dataIndex: 'instance',
    ellipsis: true,
  },
  {
    title: 'Job',
    dataIndex: 'job',
    ellipsis: true,
  },
  {
    title: 'Value',
    dataIndex: 'value',
    width: 80,
  },
];

interface TablePanelProps {
  title: string;
  data: PrometheusTableRow[];
  height?: number;
}

const TablePanel: FC<TablePanelProps> = ({ title, data, height = 180 }) => (
  <Card title={title} variant="borderless" style={{ height: '100%' }}>
    <Table<PrometheusTableRow>
      columns={columns}
      dataSource={data}
      pagination={false}
      size="small"
      rowClassName={() => 'prometheus-down-row'}
      scroll={{ y: height - 48 }}
      locale={{
        emptyText: (
          <Text type="success" strong>
            All targets are up
          </Text>
        ),
      }}
    />
    <style>{'.prometheus-down-row > td { background: #fff1f0 !important; }'}</style>
  </Card>
);

export default TablePanel;
