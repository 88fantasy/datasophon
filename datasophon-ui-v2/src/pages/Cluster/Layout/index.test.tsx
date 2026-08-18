import { render, screen, waitFor } from '@testing-library/react';
import type { CSSProperties, ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listClusters } from '@/services/cluster';
import { listAllK8sInstances } from '@/services/k8s';
import { listClusterServices } from '@/services/service';
import ClusterLayout from './index';

vi.mock('@umijs/max', () => ({
  history: {
    location: { pathname: '/cluster/7/host' },
    push: vi.fn(),
    replace: vi.fn(),
  },
  Outlet: () => <div>cluster page</div>,
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
  useLocation: () => ({ pathname: '/cluster/7/host' }),
  useParams: () => ({ clusterId: '7' }),
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({
    children,
    childrenContentStyle,
    pageHeaderRender,
  }: {
    children: ReactNode;
    childrenContentStyle?: CSSProperties;
    pageHeaderRender?: false;
  }) => (
    <div
      data-testid="cluster-page-shell"
      data-page-header-render={String(pageHeaderRender)}
      style={childrenContentStyle}
    >
      {children}
    </div>
  ),
}));

vi.mock('antd', async () => {
  const Layout = ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  );
  Layout.Sider = ({
    children,
    className,
  }: {
    children: ReactNode;
    className?: string;
  }) => (
    <aside className={className} data-testid="cluster-sider">
      {children}
    </aside>
  );
  Layout.Content = ({
    children,
    className,
  }: {
    children: ReactNode;
    className?: string;
  }) => (
    <main data-testid="cluster-content" className={className}>
      {children}
    </main>
  );

  return {
    Badge: ({ status }: { status?: string }) => (
      <i data-testid="badge" data-status={status} />
    ),
    Button: ({ children }: { children: ReactNode }) => (
      <button type="button">{children}</button>
    ),
    Dropdown: ({ children }: { children: ReactNode }) => <>{children}</>,
    Layout,
    Menu: ({
      items,
    }: {
      items: Array<{
        key?: string;
        children?: Array<{ key?: string; label?: ReactNode }>;
      }>;
    }) => (
      <nav data-testid="cluster-menu">
        {items.map((item, index) => (
          // key 与 children 分开渲染：菜单项的 textContent 必须只含 key，
          // 否则按 key 断言分组的用例会被子项文本污染
          <div key={item.key ?? `divider-${index}`}>
            <div data-testid="menu-item">{item.key ?? ''}</div>
            {item.children?.map((child) => (
              <div key={child.key} data-testid="menu-child-label">
                {child.label}
              </div>
            ))}
          </div>
        ))}
      </nav>
    ),
    Spin: () => <div>loading</div>,
    Tag: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  };
});

vi.mock('@/services/cluster', () => ({ listClusters: vi.fn() }));
vi.mock('@/services/k8s', () => ({
  listAllK8sInstances: vi.fn(),
}));
vi.mock('@/services/service', () => ({ listClusterServices: vi.fn() }));
vi.mock('../AddService/AddServiceModal', () => ({ default: () => null }));
vi.mock('../Deploy/UploadManifestModal', () => ({ default: () => null }));
vi.mock('../Deploy/UploadPackageModal', () => ({ default: () => null }));
vi.mock('./style', () => ({
  default: () => ({
    styles: new Proxy(
      {},
      {
        get: (_target, property) => `style-${String(property)}`,
      },
    ),
  }),
}));

describe('ClusterLayout', () => {
  beforeEach(() => {
    vi.mocked(listClusters).mockResolvedValue({
      data: [{ id: 7, clusterName: 'test', archType: 'physical' }],
    } as never);
    vi.mocked(listClusterServices).mockResolvedValue({ data: [] } as never);
  });

  it('keeps the cluster sidebar flush with the shared content area on every child route', async () => {
    render(<ClusterLayout />);

    const shell = await screen.findByTestId('cluster-page-shell');
    expect(shell).toHaveAttribute('data-page-header-render', 'false');
    expect(shell).toHaveStyle({ padding: '0' });
    expect(screen.getByTestId('cluster-content')).toHaveClass('style-content');
    expect(screen.getByTestId('cluster-sider')).toHaveClass('style-sider');
    expect(screen.getByText('cluster page').parentElement).toHaveClass(
      'style-outlet',
    );
    expect(screen.getByText('上传部署')).toBeInTheDocument();
    expect(screen.getByText('添加服务')).toBeInTheDocument();
    expect(screen.getByText('cluster page')).toBeInTheDocument();
  });

  it('puts the cluster overview dashboard first in the menu for physical clusters, ahead of host management', async () => {
    render(<ClusterLayout />);

    const menuItemKeys = (await screen.findAllByTestId('menu-item')).map(
      (el) => el.textContent,
    );
    expect(menuItemKeys[0]).toBe('/cluster/7/overview');
    expect(menuItemKeys[1]).toBe('/cluster/7/host');
  });

  it('does not show the cluster overview dashboard for K8s clusters', async () => {
    vi.mocked(listClusters).mockResolvedValue({
      data: [{ id: 7, clusterName: 'test', archType: 'k8s' }],
    } as never);
    vi.mocked(listAllK8sInstances).mockResolvedValue({ data: [] } as never);

    render(<ClusterLayout />);

    const menuItemKeys = (await screen.findAllByTestId('menu-item')).map(
      (el) => el.textContent,
    );
    expect(menuItemKeys).not.toContain('/cluster/7/overview');
    expect(menuItemKeys[0]).toBe('/cluster/7/host');
  });

  it('groups K8s instances by catalog instead of namespace, with a single request', async () => {
    vi.mocked(listClusters).mockResolvedValue({
      data: [{ id: 7, clusterName: 'test', archType: 'k8s' }],
    } as never);
    vi.mocked(listAllK8sInstances).mockClear();
    vi.mocked(listAllK8sInstances).mockResolvedValue({
      data: [
        {
          id: 1,
          serviceName: 'zookeeper',
          catalog: 'MIDDLEWARE',
          namespace: 'prod',
        },
        {
          id: 2,
          serviceName: 'cert-manager',
          catalog: 'ENVIRONMENT',
          namespace: 'cert-manager',
        },
        {
          id: 3,
          serviceName: 'redis-cluster',
          catalog: 'MIDDLEWARE',
          namespace: 'prod',
        },
      ],
    } as never);

    render(<ClusterLayout />);

    // 实例列表是异步加载的，等分组项渲染出来再断言
    await waitFor(() => {
      const keys = screen
        .getAllByTestId('menu-item')
        .map((el) => el.textContent);
      expect(keys).toContain('cat-MIDDLEWARE');
    });

    const menuItemKeys = screen
      .getAllByTestId('menu-item')
      .map((el) => el.textContent);
    // 分组键是 catalog 而非 namespace
    expect(menuItemKeys).toContain('cat-ENVIRONMENT');
    expect(menuItemKeys).not.toContain('ns-prod');
    // 没有实例的分类不出现
    expect(menuItemKeys).not.toContain('cat-APPLICATION');

    // 不再逐 namespace 拉取，一个集群只发一次实例请求
    expect(vi.mocked(listAllK8sInstances)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(listAllK8sInstances)).toHaveBeenCalledWith(7);
  });
});

describe('ClusterLayout takeover reconciliation', () => {
  beforeEach(() => {
    vi.mocked(listClusters).mockResolvedValue({
      data: [{ id: 7, clusterName: 'test', archType: 'k8s' }],
    } as never);
    vi.mocked(listClusterServices).mockResolvedValue({ data: [] } as never);
  });

  it('flags imported instances whose helm release disappeared', async () => {
    vi.mocked(listAllK8sInstances).mockResolvedValue({
      data: [
        {
          id: 1,
          serviceName: 'zookeeper',
          catalog: 'MIDDLEWARE',
          namespace: 'prod',
          state: 1,
          source: 'IMPORTED',
          missing: true,
        },
        {
          id: 2,
          serviceName: 'apisix',
          catalog: 'MIDDLEWARE',
          namespace: 'apisix',
          state: 1,
          source: 'IMPORTED',
          missing: false,
        },
      ],
    } as never);

    render(<ClusterLayout />);

    await waitFor(() => {
      expect(screen.getByText(/zookeeper（已失联）/)).toBeInTheDocument();
    });
    // 正常实例不加后缀
    expect(screen.getByText('apisix')).toBeInTheDocument();
    const statuses = screen
      .getAllByTestId('badge')
      .map((el) => el.getAttribute('data-status'));
    expect(statuses).toContain('error');
  });
});
