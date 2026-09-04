import { Table } from 'antd';
import { ACTIVE_TASK_COLUMNS } from './columns';
import type { DorisActiveTask } from './types';

interface ActiveTaskTableProps {
  tasks: DorisActiveTask[];
  loading?: boolean;
  onOpen?: (task: DorisActiveTask) => void;
}

const ActiveTaskTable: React.FC<ActiveTaskTableProps> = ({
  tasks,
  loading = false,
  onOpen,
}) => (
  <div data-testid="doris-active-task-table">
    <Table<DorisActiveTask>
      rowKey="taskId"
      columns={ACTIVE_TASK_COLUMNS}
      dataSource={tasks}
      loading={loading}
      pagination={false}
      scroll={{ x: 2_200 }}
      size="small"
      onRow={(task) => ({
        onClick: () => onOpen?.(task),
      })}
    />
  </div>
);

export default ActiveTaskTable;
