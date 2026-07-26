import { render, screen } from '@testing-library/react';
import type { CSSProperties, ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import MonitorDashboardLayout from './MonitorDashboardLayout';

vi.mock('@ant-design/pro-components', () => ({
  GridContent: ({
    children,
    className,
  }: {
    children: ReactNode;
    className?: string;
    style?: CSSProperties;
  }) => (
    <div className={className} data-testid="monitor-dashboard">
      {children}
    </div>
  ),
}));

vi.mock('./monitorStyles', () => ({
  default: () => ({
    styles: {
      dashboard: 'dashboard',
      embeddedDashboard: 'embedded-dashboard',
      header: 'header',
      title: 'title',
      meta: 'meta',
      metaSpin: 'meta-spin',
      content: 'content',
    },
    cx: (...classNames: Array<string | false | undefined>) =>
      classNames.filter(Boolean).join(' '),
  }),
}));

describe('MonitorDashboardLayout', () => {
  it('keeps the regular dashboard spacing by default', () => {
    render(<MonitorDashboardLayout>content</MonitorDashboardLayout>);

    expect(screen.getByTestId('monitor-dashboard')).toHaveClass('dashboard');
    expect(screen.getByTestId('monitor-dashboard')).not.toHaveClass(
      'embedded-dashboard',
    );
  });

  it('removes the dashboard outer spacing when embedded', () => {
    render(<MonitorDashboardLayout embedded>content</MonitorDashboardLayout>);

    expect(screen.getByTestId('monitor-dashboard')).toHaveClass(
      'dashboard',
      'embedded-dashboard',
    );
  });
});
