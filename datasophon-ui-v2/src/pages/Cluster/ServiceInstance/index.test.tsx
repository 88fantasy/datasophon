import { render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import { getServiceInstance, getServiceWebUis } from '@/services/service';
import ServiceInstance from './index';

const {
  apisixDashboardSpy,
  valkeyDashboardSpy,
  dsDashboardSpy,
  dorisDashboardSpy,
  nacosDashboardSpy,
  otelCollectorMonitorSpy,
  routeParams,
} = vi.hoisted(() => ({
  apisixDashboardSpy: vi.fn(),
  valkeyDashboardSpy: vi.fn(),
  dsDashboardSpy: vi.fn(),
  dorisDashboardSpy: vi.fn(),
  nacosDashboardSpy: vi.fn(),
  otelCollectorMonitorSpy: vi.fn(),
  routeParams: { clusterId: '7', instanceId: '9' },
}));

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

vi.mock('@/pages/monitor/DorisMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean }) => {
    dorisDashboardSpy(props);
    return <div>Doris dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/NacosMonitor', () => ({
  default: (props: { clusterId: number }) => {
    nacosDashboardSpy(props);
    return <div>Nacos dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/ValkeyMonitor', () => ({
  default: (props: { clusterId: number }) => {
    valkeyDashboardSpy(props);
    return <div>Valkey dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/DolphinSchedulerMonitor', () => ({
  default: (props: { clusterId: number }) => {
    dsDashboardSpy(props);
    return <div>DS dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/Cluster/ObservabilityCollector/MonitorTab', () => ({
  default: (props: { clusterId: number }) => {
    otelCollectorMonitorSpy(props);
    return <div>OTel collector monitor cluster {props.clusterId}</div>;
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
              : { serviceName: 'HDFS' },
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
              : { serviceName: 'HDFS' },
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

describe('DS service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '33';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: {
        serviceName: 'DS',
        dashboardUrl: 'http://grafana.example/ds',
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

    await screen.findByText('DS dashboard cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(dsDashboardSpy).toHaveBeenCalledWith({ clusterId: 7 }),
    );
  });

  it('opens monitoring when navigating from another service to DS', async () => {
    routeParams.instanceId = '8';
    vi.mocked(getServiceInstance).mockImplementation(
      async (_clusterId, instanceId) =>
        ({
          data:
            instanceId === 33
              ? {
                  serviceName: 'DS',
                  dashboardUrl: 'http://grafana.example/ds',
                }
              : { serviceName: 'HDFS' },
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

    routeParams.instanceId = '33';
    view.rerender(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('DS dashboard cluster 7');
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
  });
});

describe('DORIS service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '44';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: {
        serviceName: 'DORIS',
        dashboardUrl: 'http://grafana.example/doris',
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

    await screen.findByText('Doris dashboard cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(dorisDashboardSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
    );
  });
});

describe('OTELCOLLECTOR service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '66';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: { serviceName: 'OTELCOLLECTOR' },
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

    await screen.findByText('OTel collector monitor cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(otelCollectorMonitorSpy).toHaveBeenCalledWith({ clusterId: 7 }),
    );
  });
});

describe('NACOS service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '55';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: {
        serviceName: 'NACOS',
        dashboardUrl: 'http://grafana.example/nacos',
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

    await screen.findByText('Nacos dashboard cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(nacosDashboardSpy).toHaveBeenCalledWith({ clusterId: 7 }),
    );
  });
});
