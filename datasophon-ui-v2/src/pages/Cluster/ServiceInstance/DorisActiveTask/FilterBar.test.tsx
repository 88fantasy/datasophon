import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FilterBar, { TYPE_OPTIONS } from './FilterBar';

describe('FilterBar', () => {
  it('submits keyword filters and labels queue controls as experimental', () => {
    const onChange = vi.fn();
    render(
      <FilterBar
        value={{}}
        autoRefresh={false}
        onChange={onChange}
        onRefresh={vi.fn()}
        onAutoRefreshChange={vi.fn()}
      />,
    );

    fireEvent.change(
      screen.getByRole('textbox', { name: '搜索任务 ID、用户或 SQL' }),
      {
        target: { value: 'Q-1' },
      },
    );
    fireEvent.click(screen.getByRole('button', { name: '查询活动任务' }));

    expect(onChange).toHaveBeenCalledWith({ keyword: 'Q-1' });
    expect(screen.getByText('排队类型筛选 / 分组排序：实验性')).toHaveAttribute(
      'data-experimental',
      'queue-features',
    );
    expect(TYPE_OPTIONS).toContainEqual({
      label: '排队 Query（实验性）',
      value: 'QUEUED',
    });
  });
});
