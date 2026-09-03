import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBanners from './StatusBanners';
import type { DorisActiveTaskResponse } from './types';

const response = (
  overrides: Partial<DorisActiveTaskResponse> = {},
): DorisActiveTaskResponse => ({
  tasks: [],
  degraded: false,
  partialFailures: [],
  truncated: false,
  sourceTruncated: false,
  total: 0,
  returned: 0,
  connectedHostPort: 'ddh-01:9030',
  ...overrides,
});

describe('StatusBanners', () => {
  it('renders degraded, partial, and truncation notices', () => {
    render(
      <StatusBanners
        response={response({
          degraded: true,
          partialFailures: ['clientAddress'],
          truncated: true,
          sourceTruncated: true,
          total: 2_001,
        })}
      />,
    );

    expect(
      screen.getByText('当前以只读账号连接，部分字段不可用'),
    ).toBeInTheDocument();
    expect(screen.getByText(/部分数据不可用/)).toBeInTheDocument();
    expect(screen.getByText(/仅显示前 2000 条/)).toBeInTheDocument();
    expect(
      screen.getByText('数据源返回量超出上限，结果可能不完整'),
    ).toBeInTheDocument();
  });

  it('names the fields the connected Doris version cannot provide', () => {
    render(
      <StatusBanners
        response={response({
          unsupportedFields: ['spillBytes', 'loadWorkloadGroup'],
        })}
      />,
    );

    const banner = screen.getByText(/当前 Doris 版本不支持以下字段/);
    expect(banner).toHaveTextContent('溢写字节');
    expect(banner).toHaveTextContent('Load 任务的 Workload Group');
  });

  it('keeps empty and overall failure as different visual states', () => {
    const { rerender } = render(<StatusBanners response={response()} />);
    expect(screen.getByTestId('doris-active-task-status')).toHaveAttribute(
      'data-status',
      'empty',
    );

    rerender(<StatusBanners error={new Error('hidden')} />);
    expect(screen.getByTestId('doris-active-task-status')).toHaveAttribute(
      'data-status',
      'failed',
    );
    expect(
      screen.getByText('活动任务查询失败，旧快照已清除'),
    ).toBeInTheDocument();
    expect(screen.queryByText('hidden')).not.toBeInTheDocument();
  });
});
