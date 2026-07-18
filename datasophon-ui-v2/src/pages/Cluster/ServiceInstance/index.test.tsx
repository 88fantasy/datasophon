import { render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import { getServiceInstance, getServiceWebUis } from '@/services/service';
import ServiceInstance from './index';

const { apisixDashboardSpy, valkeyDashboardSpy, routeParams } = vi.hoisted(
  () => ({
    apisixDashboardSpy: vi.fn(),
    valkeyDashboardSpy: vi.fn(),
    routeParams: { clusterId: '7', instanceId: '9' },
  }),
);

vi.mock('@umijs/max', () => ({
  history: { replace: vi.fn() },
  useParams: () => routeParams,
}));

vi.mock('antd', async () => {
  const React = await import('react');
  return {
    Button: ({ children }: { children: ReactNode }) => (
      <button type="button">{children}</button>
    ),
    Dropdown: ({ children }: { children: ReactNode }) => <>{children}</>,
    Popconfirm: ({ children }: { children: ReactNode }) => <>{children}</>,
    Space: ({ children }: { children: ReactNode }) => <>{children}</>,
    Spin: () => <div>loading</div>,
    Tabs: ({
      defaultActiveKey,
      items,
    }: {
      defaultActiveKey: string;
      items: Array<{ key: string; label: ReactNode; children: ReactNode }>;
    }) => {
      const [activeKey, setActiveKey] = React.useState(defaultActiveKey);
      return (
        <div data-testid="tabs" data-active-key={activeKey}>
          <div role="tablist">
            {items.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                onClick={() => setActiveKey(item.key)}
              >
                {item.label}
              </button>
            ))}
          </div>
          {items.find((item) => item.key === activeKey)?.children}
        </div>
      );
    },
    message: { success: vi.fn() },
  };
});

vi.mock('@/pages/monitor/ApisixMonitor', () => ({
  default: (props: { clusterId: number }) => {
    apisixDashboardSpy(props);
    return <div>APISIX dashboard cluster {props.clusterId}</div>;
  },
}));

vi.mock('@/pages/monitor/DorisMonitor', () => ({ default: () => null }));
vi.mock('@/pages/monitor/NacosMonitor', () => ({ default: () => null }));
vi.mock('@/pages/monitor/ValkeyMonitor', () => ({
  default: (props: { clusterId: number }) => {
    valkeyDashboardSpy(props);
    return <div>Valkey dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/services/k8s', () => ({ listK8sResourceTypes: vi.fn() }));
vi.mock('@/services/service', () => ({
  deleteServiceInstance: vi.fn(),
  getServiceInstance: vi.fn(),
  getServiceWebUis: vi.fn(),
}));
vi.mock('./Instance', () => ({ default: () => <div>instances</div> }));
vi.mock('./K8sResource', () => ({ default: () => null }));
vi.mock('./Queue', () => ({ default: () => null }));
vi.mock('./Setting', () => ({ default: () => <div>settings</div> }));

describe('APISIX service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '9';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: {
        serviceName: 'APISIX',
        dashboardUrl: 'http://grafana.example/apisix',
      },
    } as never);
    vi.mocked(getServiceWebUis).mockResolvedValue({ data: [] } as never);
  });

  it('places monitoring first and opens it with the route cluster id', async () => {
    render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('APISIX dashboard cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(apisixDashboardSpy).toHaveBeenCalledWith({ clusterId: 7 }),
    );
  });

  it('opens monitoring when navigating from another service to APISIX', async () => {
    routeParams.instanceId = '8';
    vi.mocked(getServiceInstance).mockImplementation(
      async (_clusterId, instanceId) =>
        ({
          data:
            instanceId === 9
              ? {
                  serviceName: 'APISIX',
                  dashboardUrl: 'http://grafana.example/apisix',
                }
              : { serviceName: 'DORIS' },
        }) as never,
    );

    const view = render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('instances');
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'instance',
    );

    routeParams.instanceId = '9';
    view.rerender(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('APISIX dashboard cluster 7');
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
  });
});

describe('VALKEY service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '22';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: {
        serviceName: 'VALKEY',
        dashboardUrl: 'http://grafana.example/valkey',
      },
    } as never);
    vi.mocked(getServiceWebUis).mockResolvedValue({ data: [] } as never);
  });

  it('places monitoring first and opens it with the route cluster id', async () => {
    render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('Valkey dashboard cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(valkeyDashboardSpy).toHaveBeenCalledWith({ clusterId: 7 }),
    );
  });

  it('opens monitoring when navigating from another service to VALKEY', async () => {
    routeParams.instanceId = '8';
    vi.mocked(getServiceInstance).mockImplementation(
      async (_clusterId, instanceId) =>
        ({
          data:
            instanceId === 22
              ? {
                  serviceName: 'VALKEY',
                  dashboardUrl: 'http://grafana.example/valkey',
                }
              : { serviceName: 'DORIS' },
        }) as never,
    );

    const view = render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('instances');
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'instance',
    );

    routeParams.instanceId = '22';
    view.rerender(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('Valkey dashboard cluster 7');
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
  });
});
