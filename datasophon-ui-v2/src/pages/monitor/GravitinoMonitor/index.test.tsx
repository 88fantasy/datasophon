import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GravitinoDashboard from './index';

const mocks = vi.hoisted(() => ({
  useGravitinoDashboard: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }, values?: Record<string, string>) =>
      values?.panels ? `${id}: ${values.panels}` : id,
  }),
}));

vi.mock('./hooks/useGravitinoDashboard', () => ({
  useGravitinoDashboard: mocks.useGravitinoDashboard,
}));

vi.mock('../_shared/MonitorDashboardLayout', () => ({
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock('../_shared/PanelCol', () => ({
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock('../_shared/panels/StatPanel', () => ({
  default: () => <div />,
}));
vi.mock('../_shared/panels/TimeSeriesPanel', () => ({
  default: () => <div />,
}));
vi.mock('./toolbar/GravitinoDashboardToolbar', () => ({
  default: () => <div />,
}));

describe('GravitinoDashboard partial failures', () => {
  beforeEach(() => {
    mocks.useGravitinoDashboard.mockReturnValue({
      instant: {
        nodeCount: 1,
        httpQps: 0,
        jettyThreadUsage: 10,
        queuedRequests: Number.NaN,
        activeConnections: 1,
        heapUsage: 20,
      },
      series: {},
      instances: [],
      loading: false,
      failedPanelIds: ['G04', 'G13'],
    });
  });

  it('surfaces the failed panel ids without hiding the rest of the dashboard', () => {
    render(<GravitinoDashboard clusterId={1} />);

    expect(
      screen.getByText('pages.gravitinoMonitor.partialLoad.title'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'pages.gravitinoMonitor.partialLoad.description: G04, G13',
      ),
    ).toBeInTheDocument();
  });
});
