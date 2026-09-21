import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import Dashboard from './index';

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  services: vi.fn(),
  otel: vi.fn(),
  summary: vi.fn(),
}));
vi.mock('@ant-design/pro-components', () => ({
  GridContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock('@umijs/max', () => ({
  history: { push: mocks.push },
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
vi.mock('@/services/service', () => ({ listClusterServices: mocks.services }));
vi.mock('./hooks/useClusterOtelPanels', () => ({
  useClusterOtelPanels: mocks.otel,
}));
vi.mock('./hooks/useClusterSummary', () => ({
  useClusterSummary: mocks.summary,
}));
vi.mock('../../monitor/_shared/panels/TimeSeriesPanel', () => ({
  default: ({
    title,
    loading,
    error,
  }: {
    title: string;
    loading?: boolean;
    error?: boolean;
  }) => (
    <div data-testid={title}>
      {loading ? 'loading' : error ? 'error' : 'ready'}
    </div>
  ),
}));
vi.mock('./panels/AlertTrendPanel', () => ({ default: () => null }));
vi.mock('../../monitor/_shared/DashboardToolbar', () => ({
  default: ({ onRefresh }: { onRefresh: () => void }) => (
    <button type="button" onClick={onRefresh}>
      刷新
    </button>
  ),
}));

const dashboard = (
  clusterId = 3,
  serviceList = [
    { id: 12, serviceName: 'DORIS' },
  ] as DATASOPHON.ServiceInstanceInfo[],
) => (
  <App>
    <ClusterContext.Provider
      value={{
        clusterId,
        clusterInfo: {} as DATASOPHON.ClusterInfo,
        serviceList,
      }}
    >
      <Dashboard />
    </ClusterContext.Provider>
  </App>
);
const mount = () => render(dashboard());

describe('cluster dashboard first-round behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.otel.mockReturnValue({
      failedPanelIds: [],
      cpuSeries: [],
      networkSeries: [],
      loading: false,
      cpuPercent: NaN,
      memoryPercent: NaN,
      diskPercent: NaN,
    });
    mocks.summary.mockReturnValue({
      loading: false,
      recentAlerts: [
        { id: 7, alertTargetName: 'Doris 告警', serviceInstanceId: 12 },
      ],
      summary: {
        serviceHealth: [
          {
            serviceName: 'DORIS',
            label: 'Doris 服务',
            healthPercent: null,
            runningRoles: 1,
            totalRoles: 1,
            serviceState: '正常',
          },
        ],
      },
    });
  });

  it('shows query failure and retry without claiming an empty successful result', () => {
    mocks.otel.mockReturnValue({
      ...mocks.otel(),
      failedPanelIds: ['CO-CPU'],
      error: 'query unavailable',
    });
    mount();
    expect(screen.getByText('监控指标查询失败')).toBeInTheDocument();
    expect(screen.getByText(/query unavailable/)).toBeInTheDocument();
    expect(
      screen.getByTestId('pages.clusterDashboard.panel.cpu'),
    ).toHaveTextContent('error');
    fireEvent.click(screen.getByText('重试'));
    expect(mocks.otel).toHaveBeenLastCalledWith(
      expect.objectContaining({ refreshKey: 1 }),
    );
  });

  it('shows each metric panel state independently', () => {
    mocks.otel.mockReturnValue({ ...mocks.otel(), failedPanelIds: ['CO-CPU'] });
    const { rerender } = mount();
    expect(
      screen.getByTestId('pages.clusterDashboard.panel.cpu'),
    ).toHaveTextContent('error');
    expect(
      screen.getByTestId('pages.clusterDashboard.panel.network'),
    ).toHaveTextContent('ready');
    mocks.otel.mockReturnValue({ ...mocks.otel(), loading: true });
    rerender(
      <App>
        <Dashboard />
      </App>,
    );
    expect(
      screen.getByTestId('pages.clusterDashboard.panel.cpu'),
    ).toHaveTextContent('loading');
  });

  it('resolves a service name to its real instance id and links alert targets directly', async () => {
    mount();
    fireEvent.click(screen.getByText('Doris 服务'));
    await waitFor(() =>
      expect(mocks.push).toHaveBeenCalledWith('/cluster/3/service/12'),
    );
    expect(mocks.services).not.toHaveBeenCalled();
    mocks.push.mockClear();
    fireEvent.click(screen.getByText('Doris 告警'));
    expect(mocks.push).toHaveBeenCalledWith('/cluster/3/service/12');
  });

  it('uses the current cluster list after switching clusters', () => {
    const view = mount();
    view.rerender(
      dashboard(4, [
        { id: 23, serviceName: 'DORIS' },
      ] as DATASOPHON.ServiceInstanceInfo[]),
    );
    fireEvent.click(screen.getByText('Doris 服务'));
    expect(mocks.push).toHaveBeenCalledExactlyOnceWith('/cluster/4/service/23');
    expect(mocks.services).not.toHaveBeenCalled();
  });

  it('warns without navigating while the shared list is empty', async () => {
    render(dashboard(3, []));
    fireEvent.click(screen.getByText('Doris 服务'));
    expect(
      await screen.findByText('未找到对应服务实例，请刷新后重试'),
    ).toBeInTheDocument();
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.services).not.toHaveBeenCalled();
  });

  it.each([
    true,
    false,
  ])('labels only the failed summary endpoint when summaryFailed=%s', (summaryFailed) => {
    mocks.summary.mockReturnValue({
      loading: false,
      recentAlerts: [],
      summary: { serviceHealth: [] },
      error: 'unavailable',
      summaryFailed,
      alertsFailed: !summaryFailed,
    });
    mount();
    expect(
      screen.getByText(
        summaryFailed ? '服务数据查询失败，请重试' : '告警数据查询失败，请重试',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(
        summaryFailed ? '告警数据查询失败，请重试' : '服务数据查询失败，请重试',
      ),
    ).not.toBeInTheDocument();
  });
});
