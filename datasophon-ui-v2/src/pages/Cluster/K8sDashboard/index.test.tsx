import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import { getK8sDashboard } from '@/services/k8s';
import K8sDashboard from './index';

vi.mock('@ant-design/plots', () => ({
  Bar: () => <div>bar chart</div>,
  Line: () => <div>line chart</div>,
}));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: () => <div data-testid="nested-page-container" />,
}));
vi.mock('@/services/k8s', () => ({ getK8sDashboard: vi.fn() }));

describe('K8sDashboard', () => {
  beforeEach(() => {
    vi.mocked(getK8sDashboard).mockResolvedValue({
      data: {
        observedAt: '2026-07-26T08:00:00Z',
        telemetry: { status: 'AVAILABLE' },
        overview: {
          health: 'HEALTHY',
          readyNodes: 3,
          totalNodes: 3,
          runningPods: 10,
          totalPods: 10,
          critical: 0,
          warning: 0,
        },
        trends: [],
        namespaces: [],
        capacities: [],
        events: [],
        workloads: [],
      },
    } as never);
  });

  it('keeps the header controls without a nested page container', async () => {
    render(
      <ClusterContext.Provider
        value={{ clusterId: 7, clusterInfo: { archType: 'k8s' } } as never}
      >
        <K8sDashboard />
      </ClusterContext.Provider>,
    );

    expect(
      await screen.findByRole('heading', { name: /K8s 集群运行概览/ }),
    ).toBeInTheDocument();
    expect(screen.getByText(/更新时间：/)).toBeInTheDocument();
    expect(screen.getByText('1h')).toBeInTheDocument();
    expect(screen.getByText('6h')).toBeInTheDocument();
    expect(screen.getByText('24h')).toBeInTheDocument();
    expect(
      screen.queryByTestId('nested-page-container'),
    ).not.toBeInTheDocument();
  });
});
