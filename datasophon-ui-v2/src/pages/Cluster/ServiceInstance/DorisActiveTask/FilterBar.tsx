import { Button, Input, InputNumber, Select, Space, Switch, Tag } from 'antd';
import { useEffect, useState } from 'react';
import type { DorisActiveTaskQuery } from './types';

interface FilterBarProps {
  value: DorisActiveTaskQuery;
  loading?: boolean;
  autoRefresh: boolean;
  onChange: (value: DorisActiveTaskQuery) => void;
  onRefresh: () => void;
  onAutoRefreshChange: (enabled: boolean) => void;
}

export const TYPE_OPTIONS = [
  { label: 'Query', value: 'QUERY' },
  { label: 'Load', value: 'LOAD' },
  { label: '排队 Query（实验性）', value: 'QUEUED' },
];

const FilterBar: React.FC<FilterBarProps> = ({
  value,
  loading = false,
  autoRefresh,
  onChange,
  onRefresh,
  onAutoRefreshChange,
}) => {
  const [draft, setDraft] = useState<DorisActiveTaskQuery>(value);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  const update = <K extends keyof DorisActiveTaskQuery>(
    key: K,
    nextValue: DorisActiveTaskQuery[K],
  ) => {
    setDraft((current) => ({ ...current, [key]: nextValue }));
  };

  return (
    <Space wrap data-testid="doris-active-task-filters">
      <Input
        aria-label="搜索任务 ID、用户或 SQL"
        placeholder="任务 ID / 用户 / SQL"
        value={draft.keyword ?? ''}
        onChange={(event) => update('keyword', event.target.value)}
        onPressEnter={() => onChange(draft)}
        style={{ width: 230 }}
      />
      <Select
        aria-label="任务类型"
        mode="multiple"
        allowClear
        placeholder="任务类型"
        options={TYPE_OPTIONS}
        value={draft.types ?? []}
        onChange={(types: string[]) => update('types', types)}
        style={{ width: 220 }}
      />
      <Input
        aria-label="用户"
        placeholder="用户"
        value={draft.user ?? ''}
        onChange={(event) => update('user', event.target.value)}
        style={{ width: 130 }}
      />
      <Input
        aria-label="来源 FE"
        placeholder="来源 FE"
        value={draft.feHost ?? ''}
        onChange={(event) => update('feHost', event.target.value)}
        style={{ width: 140 }}
      />
      <InputNumber
        aria-label="内存大于等于"
        min={0}
        placeholder="内存 ≥ bytes"
        value={draft.minMemoryBytes ?? null}
        onChange={(nextValue) => update('minMemoryBytes', nextValue)}
        style={{ width: 150 }}
      />
      <InputNumber
        aria-label="时长大于等于"
        min={0}
        placeholder="时长 ≥ ms"
        value={draft.minElapsedMs ?? null}
        onChange={(nextValue) => update('minElapsedMs', nextValue)}
        style={{ width: 140 }}
      />
      <Button
        aria-label="查询活动任务"
        type="primary"
        onClick={() => onChange(draft)}
      >
        查询
      </Button>
      <Button onClick={onRefresh} loading={loading}>
        手动刷新
      </Button>
      <Space size={4}>
        <Switch
          aria-label="10 秒自动刷新"
          checked={autoRefresh}
          onChange={onAutoRefreshChange}
        />
        <span>10 秒自动刷新</span>
      </Space>
      <Tag color="gold" data-experimental="queue-features">
        排队类型筛选 / 分组排序：实验性
      </Tag>
    </Space>
  );
};

export default FilterBar;
