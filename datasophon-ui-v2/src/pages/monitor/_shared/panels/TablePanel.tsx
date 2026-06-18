import { Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { FC } from 'react';
import MonitorPanelCard from '../MonitorPanelCard';
import useStyles from '../monitorStyles';
import type { PrometheusTableRow } from '../types';

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

const TablePanel: FC<TablePanelProps> = ({ title, data, height = 180 }) => {
  const { styles } = useStyles();

  return (
    <MonitorPanelCard title={title}>
      <Table<PrometheusTableRow>
        columns={columns}
        dataSource={data}
        pagination={false}
        size="small"
        rowClassName={() => styles.tableDownRow}
        scroll={{ y: height - 48 }}
        locale={{
          emptyText: (
            <Text type="success" strong>
              All targets are up
            </Text>
          ),
        }}
      />
    </MonitorPanelCard>
  );
};

export default TablePanel;
