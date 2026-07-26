import { render, screen } from '@testing-library/react';
import type { CSSProperties, ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listClusters } from '@/services/cluster';
import { listK8sInstances, listK8sNamespaces } from '@/services/k8s';
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
  Layout.Sider = ({ children }: { children: ReactNode }) => (
    <aside>{children}</aside>
  );
  Layout.Content = ({ children }: { children: ReactNode }) => (
    <main>{children}</main>
  );

  return {
    Badge: () => null,
    Button: ({ children }: { children: ReactNode }) => (
      <button type="button">{children}</button>
    ),
    Dropdown: ({ children }: { children: ReactNode }) => <>{children}</>,
    Layout,
    Menu: ({ items }: { items: Array<{ key?: string }> }) => (
      <nav data-testid="cluster-menu">
        {items.map((item, index) => (
          <div key={item.key ?? `divider-${index}`} data-testid="menu-item">
            {item.key ?? ''}
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
  listK8sInstances: vi.fn(),
  listK8sNamespaces: vi.fn(),
}));
vi.mock('@/services/service', () => ({ listClusterServices: vi.fn() }));
vi.mock('../AddService/AddServiceModal', () => ({ default: () => null }));
vi.mock('../Deploy/UploadManifestModal', () => ({ default: () => null }));
vi.mock('../Deploy/UploadPackageModal', () => ({ default: () => null }));

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
    vi.mocked(listK8sNamespaces).mockResolvedValue({ data: [] } as never);
    vi.mocked(listK8sInstances).mockResolvedValue({ data: [] } as never);

    render(<ClusterLayout />);

    const menuItemKeys = (await screen.findAllByTestId('menu-item')).map(
      (el) => el.textContent,
    );
    expect(menuItemKeys).not.toContain('/cluster/7/overview');
    expect(menuItemKeys[0]).toBe('/cluster/7/host');
  });
});
