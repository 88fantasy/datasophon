import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import {
  cancelTakeover,
  getK8sInstance,
  listK8sResourceTypes,
} from '@/services/k8s';
import { getServiceInstance, getServiceWebUis } from '@/services/service';
import ServiceInstance from './index';

const {
  apisixDashboardSpy,
  apisixGatewaySpy,
  valkeyDashboardSpy,
  dsDashboardSpy,
  dsWorkflowSpy,
  dorisDashboardSpy,
  nacosDashboardSpy,
  gravitinoDashboardSpy,
  otelCollectorMonitorSpy,
  zookeeperDashboardSpy,
  kyuubiDashboardSpy,
  juicefsDashboardSpy,
  routeParams,
  accessState,
} = vi.hoisted(() => ({
  apisixDashboardSpy: vi.fn(),
  apisixGatewaySpy: vi.fn(),
  valkeyDashboardSpy: vi.fn(),
  dsDashboardSpy: vi.fn(),
  dsWorkflowSpy: vi.fn(),
  dorisDashboardSpy: vi.fn(),
  nacosDashboardSpy: vi.fn(),
  gravitinoDashboardSpy: vi.fn(),
  otelCollectorMonitorSpy: vi.fn(),
  zookeeperDashboardSpy: vi.fn(),
  kyuubiDashboardSpy: vi.fn(),
  juicefsDashboardSpy: vi.fn(),
  routeParams: { clusterId: '7', instanceId: '9' },
  accessState: { canAdmin: false },
}));

vi.mock('@umijs/max', () => ({
  history: { replace: vi.fn() },
  useAccess: () => accessState,
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) =>
      id === 'dsWorkflow.tab'
        ? '工作流'
        : id === 'dorisActiveTask.tab'
          ? '活动任务'
          : id,
  }),
  useParams: () => routeParams,
}));

vi.mock('antd', async () => {
  const React = await import('react');
  return {
    Button: ({ children }: { children: ReactNode }) => (
      <button type="button">{children}</button>
    ),
    Dropdown: ({ children }: { children: ReactNode }) => <>{children}</>,
    // 真实 Popconfirm 要先弹确认框；这里把点击直接当作「已确认」，
    // 便于验证按钮真的接到了 onConfirm
    Popconfirm: ({
      children,
      onConfirm,
    }: {
      children: ReactNode;
      onConfirm?: () => void;
    }) => (
      <span
        onClick={() => onConfirm?.()}
        onKeyDown={() => onConfirm?.()}
        role="presentation"
      >
        {children}
      </span>
    ),
    Space: ({ children }: { children: ReactNode }) => <>{children}</>,
    Spin: () => <div>loading</div>,
    Tabs: ({
      defaultActiveKey,
      items,
      tabBarExtraContent,
    }: {
      defaultActiveKey: string;
      items: Array<{ key: string; label: ReactNode; children: ReactNode }>;
      tabBarExtraContent?: { right?: ReactNode };
    }) => {
      const [activeKey, setActiveKey] = React.useState(defaultActiveKey);
      return (
        <div data-testid="tabs" data-active-key={activeKey}>
          {tabBarExtraContent?.right}
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
  default: (props: { clusterId: number; embedded?: boolean }) => {
    apisixDashboardSpy(props);
    return <div>APISIX dashboard cluster {props.clusterId}</div>;
  },
}));

vi.mock('@/pages/monitor/DorisMonitor', () => ({
  default: (props: {
    clusterId: number;
    embedded?: boolean;
    job?: string;
    monitorProfile?: string;
  }) => {
    dorisDashboardSpy(props);
    return (
      <div>
        Doris dashboard cluster {props.clusterId}
        {props.monitorProfile ? ` profile ${props.monitorProfile}` : ''}
      </div>
    );
  },
}));
vi.mock('@/pages/monitor/NacosMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean }) => {
    nacosDashboardSpy(props);
    return <div>Nacos dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/GravitinoMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean }) => {
    gravitinoDashboardSpy(props);
    return <div>Gravitino dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/ValkeyMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean }) => {
    valkeyDashboardSpy(props);
    return <div>Valkey dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/DolphinSchedulerMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean }) => {
    dsDashboardSpy(props);
    return <div>DS dashboard cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/Cluster/ObservabilityCollector/MonitorTab', () => ({
  default: (props: { clusterId: number; embedded?: boolean }) => {
    otelCollectorMonitorSpy(props);
    return <div>OTel collector monitor cluster {props.clusterId}</div>;
  },
}));
vi.mock('@/pages/monitor/ZooKeeperMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean; job?: string }) => {
    zookeeperDashboardSpy(props);
    return <div>ZooKeeper dashboard job {props.job ?? 'none'}</div>;
  },
}));
vi.mock('@/pages/monitor/KyuubiMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean; job?: string }) => {
    kyuubiDashboardSpy(props);
    return <div>Kyuubi dashboard job {props.job ?? 'none'}</div>;
  },
}));
vi.mock('@/pages/monitor/JuiceFSMonitor', () => ({
  default: (props: { clusterId: number; embedded?: boolean; job?: string }) => {
    juicefsDashboardSpy(props);
    return <div>JuiceFS dashboard job {props.job ?? 'none'}</div>;
  },
}));
vi.mock('@/services/k8s', () => ({
  cancelTakeover: vi.fn(),
  getK8sInstance: vi.fn(),
  listK8sResourceTypes: vi.fn(),
}));
vi.mock('@/services/service', () => ({
  deleteServiceInstance: vi.fn(),
  getServiceInstance: vi.fn(),
  getServiceWebUis: vi.fn(),
}));
vi.mock('./ApisixGateway', () => ({
  default: (props: { clusterId: number; instanceId: number }) => {
    apisixGatewaySpy(props);
    return <div>gateway config</div>;
  },
}));
vi.mock('./DsWorkflow', () => ({
  default: (props: { clusterId: number; instanceId: number }) => {
    dsWorkflowSpy(props);
    return <div>DS workflow project selector</div>;
  },
}));
vi.mock('./Instance', () => ({ default: () => <div>instances</div> }));
vi.mock('./K8sResource', () => ({ default: () => null }));
vi.mock('./Queue', () => ({ default: () => null }));
vi.mock('./Setting', () => ({ default: () => <div>settings</div> }));
vi.mock('./Setting/ImportedValuesViewer', () => ({
  default: (props: { releaseName?: string }) => (
    <div>imported values of {props.releaseName ?? 'unknown'}</div>
  ),
}));

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

    expect(tabs).toEqual(['监控', '网关配置', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(apisixDashboardSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
    );

    fireEvent.click(screen.getByRole('tab', { name: '网关配置' }));
    await screen.findByText('gateway config');
    expect(apisixGatewaySpy).toHaveBeenCalledWith({
      clusterId: 7,
      instanceId: 9,
    });
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
      expect(valkeyDashboardSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
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

    expect(tabs).toEqual(['监控', '工作流', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(dsDashboardSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
    );

    fireEvent.click(screen.getByRole('tab', { name: '工作流' }));
    await screen.findByText('DS workflow project selector');
    expect(dsWorkflowSpy).toHaveBeenCalledWith({
      clusterId: 7,
      instanceId: 33,
    });
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
    accessState.canAdmin = false;
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

  it('shows the activity tab after monitoring for admins', async () => {
    accessState.canAdmin = true;
    render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'physical' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

    await screen.findByText('Doris dashboard cluster 7');

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
      '监控',
      '活动任务',
      '概览',
      '实例',
      '配置',
    ]);
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
      expect(otelCollectorMonitorSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
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
      expect(nacosDashboardSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
    );
  });
});

describe('GRAVITINO service instance tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '55';
    vi.mocked(getServiceInstance).mockResolvedValue({
      data: {
        serviceName: 'GRAVITINO',
        dashboardUrl: 'http://grafana.example/gravitino',
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

    await screen.findByText('Gravitino dashboard cluster 7');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);

    expect(tabs).toEqual(['监控', '概览', '实例', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    await waitFor(() =>
      expect(gravitinoDashboardSpy).toHaveBeenCalledWith({
        clusterId: 7,
        embedded: true,
      }),
    );
  });
});

describe('K8s service instance monitoring tab', () => {
  const renderK8s = () =>
    render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'k8s' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '101';
    vi.mocked(listK8sResourceTypes).mockResolvedValue({
      data: ['Pod'],
    } as never);
  });

  it('puts monitoring first and hands the registered metricsJob to the dashboard', async () => {
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: { id: 101, serviceName: 'zookeeper', metricsJob: 'zookeeper' },
    } as never);

    renderK8s();

    await screen.findByText('ZooKeeper dashboard job zookeeper');
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);
    expect(tabs).toEqual(['监控', 'Pod', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'monitor',
    );
    expect(zookeeperDashboardSpy).toHaveBeenCalledWith({
      clusterId: 7,
      embedded: true,
      job: 'zookeeper',
    });
  });

  it('hands the registered monitorProfile through to a CR-backed Doris dashboard', async () => {
    const monitorProfile = JSON.stringify({
      profile: 'doris-disaggregated',
      roles: { fe: ['doris-fe'], compute: ['doris-cg1', 'doris-cg2'] },
    });
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: {
        id: 101,
        serviceName: 'doris-disaggregated',
        metricsJob: 'doris-fe,doris-cg1,doris-cg2',
        sourceKind: 'CR',
        monitorProfile,
      },
    } as never);

    renderK8s();

    await screen.findByText(
      `Doris dashboard cluster 7 profile ${monitorProfile}`,
    );
    expect(dorisDashboardSpy).toHaveBeenCalledWith({
      clusterId: 7,
      embedded: true,
      job: 'doris-fe,doris-cg1,doris-cg2',
      monitorProfile,
    });
  });

  it('hands the registered monitorProfile through to a CR-backed coupled Doris dashboard', async () => {
    const monitorProfile = JSON.stringify({
      profile: 'doris-coupled',
      roles: { fe: ['mycluster-fe'], be: ['mycluster-be'] },
    });
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: {
        id: 102,
        serviceName: 'doris-coupled',
        metricsJob: 'mycluster-fe,mycluster-be',
        sourceKind: 'CR',
        monitorProfile,
      },
    } as never);

    renderK8s();

    await screen.findByText(
      `Doris dashboard cluster 7 profile ${monitorProfile}`,
    );
    expect(dorisDashboardSpy).toHaveBeenCalledWith({
      clusterId: 7,
      embedded: true,
      job: 'mycluster-fe,mycluster-be',
      monitorProfile,
    });
  });

  it('passes a comma-joined metricsJob through untouched', async () => {
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: {
        id: 101,
        serviceName: 'kyuubi',
        metricsJob: 'kyuubi-a,kyuubi-b',
      },
    } as never);

    renderK8s();

    await screen.findByText('Kyuubi dashboard job kyuubi-a,kyuubi-b');
    expect(kyuubiDashboardSpy).toHaveBeenCalledWith({
      clusterId: 7,
      embedded: true,
      job: 'kyuubi-a,kyuubi-b',
    });
  });

  it('omits the monitoring tab for services without a dashboard', async () => {
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: { id: 101, serviceName: 'cert-manager' },
    } as never);

    renderK8s();

    const tabs = (await screen.findAllByRole('tab')).map(
      (tab) => tab.textContent,
    );
    expect(tabs).toEqual(['Pod', '配置']);
    expect(screen.getByTestId('tabs')).toHaveAttribute(
      'data-active-key',
      'Pod',
    );
    expect(zookeeperDashboardSpy).not.toHaveBeenCalled();
    expect(juicefsDashboardSpy).not.toHaveBeenCalled();
  });
});

describe('K8s imported instance read-only lockdown', () => {
  const renderK8s = () =>
    render(
      <ClusterContext.Provider
        value={{ clusterInfo: { archType: 'k8s' } } as never}
      >
        <ServiceInstance />
      </ClusterContext.Provider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    routeParams.clusterId = '7';
    routeParams.instanceId = '101';
    vi.mocked(listK8sResourceTypes).mockResolvedValue({
      data: ['Pod'],
    } as never);
  });

  it('shows read-only values and a cancel-takeover action for imported instances', async () => {
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: {
        id: 101,
        serviceName: 'zookeeper',
        source: 'IMPORTED',
        releaseName: 'zookeeper',
        metricsJob: 'zookeeper',
      },
    } as never);
    vi.mocked(cancelTakeover).mockResolvedValue({} as never);

    renderK8s();

    await screen.findByText('ZooKeeper dashboard job zookeeper');
    // 「删除服务」不能出现——它会走到 helm uninstall
    expect(screen.queryByText('删除服务')).not.toBeInTheDocument();
    expect(screen.getByText('取消接管')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: '配置' }));
    await screen.findByText('imported values of zookeeper');
    expect(screen.queryByText('settings')).not.toBeInTheDocument();

    fireEvent.click(screen.getByText('取消接管'));
    await waitFor(() => expect(cancelTakeover).toHaveBeenCalledWith(7, 101));
  });

  it('keeps the editable config tab for platform-installed instances', async () => {
    vi.mocked(getK8sInstance).mockResolvedValue({
      data: { id: 101, serviceName: 'zookeeper', source: 'INSTALLED' },
    } as never);

    renderK8s();

    await screen.findAllByRole('tab');
    expect(screen.queryByText('取消接管')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: '配置' }));
    await screen.findByText('settings');
  });
});
