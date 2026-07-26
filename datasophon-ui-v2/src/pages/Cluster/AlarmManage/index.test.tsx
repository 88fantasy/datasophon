import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import AlarmManage from './index';

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: () => <div data-testid="nested-page-container" />,
}));
vi.mock('./Group', () => ({ default: () => <div>alarm groups</div> }));
vi.mock('./Metric', () => ({ default: () => <div>alarm metrics</div> }));

describe('AlarmManage', () => {
  it('keeps the title and tabs without a nested page container', () => {
    render(<AlarmManage />);

    expect(
      screen.getByRole('heading', { name: '告警管理' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '告警组' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '告警指标' })).toBeInTheDocument();
    expect(
      screen.queryByTestId('nested-page-container'),
    ).not.toBeInTheDocument();
  });
});
