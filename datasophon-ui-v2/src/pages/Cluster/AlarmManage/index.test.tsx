import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AlarmManage from './index';

const routerMocks = vi.hoisted(() => ({
  params: new URLSearchParams(),
  setSearchParams: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useSearchParams: () => [routerMocks.params, routerMocks.setSearchParams],
}));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: () => <div data-testid="nested-page-container" />,
}));
vi.mock('./Group', () => ({ default: () => <div>alarm groups</div> }));
vi.mock('./Metric', () => ({ default: () => <div>alarm metrics</div> }));
vi.mock('./History', () => ({ default: () => <div>alarm history</div> }));
vi.mock('../Dashboard/hooks/useClusterSummary', () => ({
  useClusterSummary: () => ({
    summary: {
      stats: {
        alertTotal: 3,
        criticalAlertTotal: 1,
      },
    },
    recentAlerts: [],
    loading: false,
  }),
}));

describe('AlarmManage', () => {
  beforeEach(() => {
    routerMocks.params.delete('tab');
    routerMocks.setSearchParams.mockReset();
  });

  it('keeps the title and tabs without a nested page container', () => {
    render(<AlarmManage />);

    expect(
      screen.getByRole('heading', { name: '告警管理' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '告警组' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '告警指标' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '告警历史' })).toBeInTheDocument();
    expect(screen.getByText('活跃告警')).toBeInTheDocument();
    expect(screen.getByText('严重告警')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '查看告警历史' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('nested-page-container'),
    ).not.toBeInTheDocument();
  });

  it('opens the history tab from the tab query parameter', () => {
    routerMocks.params.set('tab', 'history');

    render(<AlarmManage />);

    expect(screen.getByRole('tab', { name: '告警历史' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('writes the selected tab to the query parameter', () => {
    render(<AlarmManage />);

    fireEvent.click(screen.getByRole('tab', { name: '告警历史' }));

    expect(routerMocks.setSearchParams).toHaveBeenCalledOnce();
    const [params, options] = routerMocks.setSearchParams.mock.calls[0];
    expect(params.get('tab')).toBe('history');
    expect(options).toEqual({ replace: true });
  });
});
