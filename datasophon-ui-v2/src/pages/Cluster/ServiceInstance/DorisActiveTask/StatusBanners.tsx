import { Alert, Empty, Space, Spin } from 'antd';
import type { DorisActiveTaskResponse } from './types';

interface StatusBannersProps {
  response?: DorisActiveTaskResponse | null;
  error?: unknown;
  loading?: boolean;
}

const FAILURE_LABELS: Record<string, string> = {
  clientAddress: '客户端地址',
  workloadGroup: 'Workload Group',
};

// 与后端 DorisVersionProfile.unsupportedFields 的取值一一对应。
const UNSUPPORTED_LABELS: Record<string, string> = {
  spillBytes: '溢写字节',
  loadWorkloadGroup: 'Load 任务的 Workload Group',
};

const StatusBanners: React.FC<StatusBannersProps> = ({
  response,
  error,
  loading = false,
}) => {
  if (error) {
    return (
      <div data-status="failed" data-testid="doris-active-task-status">
        <Alert showIcon type="error" title="活动任务查询失败，旧快照已清除" />
      </div>
    );
  }

  if (!response && loading) {
    return (
      <div data-status="loading" data-testid="doris-active-task-status">
        <Spin tip="加载中" />
      </div>
    );
  }

  if (!response) {
    return (
      <div data-status="empty" data-testid="doris-active-task-status">
        <Empty description="暂无活动任务" />
      </div>
    );
  }

  const partialFailures = response.partialFailures ?? [];
  const unsupportedFields = response.unsupportedFields ?? [];
  return (
    <Space
      orientation="vertical"
      style={{ width: '100%' }}
      data-status={response.tasks.length === 0 ? 'empty' : undefined}
      data-testid="doris-active-task-status"
    >
      {response.degraded ? (
        <Alert
          showIcon
          type="warning"
          data-status="degraded"
          title="当前以只读账号连接，部分字段不可用"
        />
      ) : null}
      {partialFailures.length > 0 ? (
        <Alert
          showIcon
          type="warning"
          data-status="partial"
          title={`部分数据不可用：${partialFailures
            .map((failure) => FAILURE_LABELS[failure] ?? '部分视图')
            .join('、')}`}
        />
      ) : null}
      {unsupportedFields.length > 0 ? (
        <Alert
          showIcon
          type="info"
          data-status="version-unsupported"
          title={`当前 Doris 版本不支持以下字段，列内显示为空：${unsupportedFields
            .map((field) => UNSUPPORTED_LABELS[field] ?? field)
            .join('、')}`}
        />
      ) : null}
      {response.truncated ? (
        <Alert
          showIcon
          type="info"
          data-status="truncated"
          title={`仅显示前 2000 条，共 ${response.total} 条`}
        />
      ) : null}
      {response.sourceTruncated ? (
        <Alert
          showIcon
          type="info"
          data-status="source-truncated"
          title="数据源返回量超出上限，结果可能不完整"
        />
      ) : null}
      {response.tasks.length === 0 ? (
        <div data-status="empty">
          <Empty description="暂无活动任务" />
        </div>
      ) : null}
    </Space>
  );
};

export default StatusBanners;
