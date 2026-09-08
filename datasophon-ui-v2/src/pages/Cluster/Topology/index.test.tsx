import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listClusters } from '@/services/cluster';
import ClusterTopology from './index';
import { loadTopology } from './topologyData';
import type { TopologySceneProps, TopologySnapshot } from './types';

const routing = vi.hoisted(() => ({ clusterId: '7', push: vi.fn() }));
vi.mock('@umijs/max', () => ({
  useParams: () => ({ clusterId: routing.clusterId }),
  history: { push: routing.push },
}));
vi.mock('@/services/cluster', () => ({ listClusters: vi.fn() }));
vi.mock('./topologyData', () => ({ loadTopology: vi.fn() }));
vi.mock('./TopologyScene', () => ({
  default: (props: TopologySceneProps) => (
    <div
      data-testid="scene"
      data-mode={props.mode}
      data-nodes={props.nodes.map((node) => node.id).join(',')}
      data-zone={props.focusZone}
      data-selected={props.selectedId}
    >
      <button type="button" onClick={() => props.onSelect('mysql')}>
        选择场景中的 MySQL
      </button>
      <button
        type="button"
        onClick={() => props.onError('WebGL 不可用，已切换平面视图。')}
      >
        模拟 WebGL 失败
      </button>
    </div>
  ),
}));
vi.mock('./TopologyPlan', () => ({
  default: (props: TopologySceneProps) => (
    <div
      data-testid="plan"
      data-zone={props.focusZone}
      data-selected={props.selectedId}
    />
  ),
}));

const snapshot: TopologySnapshot = {
  nodes: [
    {
      id: 'host-1',
      label: '10.0.0.1',
      subtitle: 'vm',
      kind: 'host',
      zone: 'vm',
      state: 'running',
      source: 'inventory',
      instances: [],
      details: [],
    },
    {
      parentId: 'host-1',
      id: 'mysql',
      resourceId: 'mysql',
      label: 'MySQL',
      subtitle: '3306/TCP',
      kind: 'service',
      zone: 'vm',
      state: 'running',
      source: 'inventory',
      serviceName: 'MYSQL',
      instances: [],
      details: [],
    },
  ],
  edges: [],
  warnings: [],
  observedAt: '2026-09-07T07:00:00Z',
};

async function openPage() {
  await act(async () => {
    render(<ClusterTopology />);
  });
  // React.lazy 的 import promise 也需要完成后再操作模式控件。
  await act(async () => {
    await Promise.resolve();
  });
}

describe('cluster topology page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routing.clusterId = '7';
    vi.mocked(listClusters).mockResolvedValue({
      data: [
        {
          id: 7,
          clusterName: '本地测试集群',
          clusterCode: 'qa',
          archType: 'physical',
        },
      ],
    });
    vi.mocked(loadTopology).mockResolvedValue(snapshot);
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('defaults to 3D and preserves zone and selection through three modes and theme changes', async () => {
    await openPage();
    expect(screen.getByTestId('scene')).toHaveAttribute('data-mode', '3d');
    fireEvent.click(screen.getByRole('button', { name: '进入VM集群分区' }));
    expect(
      screen.queryByRole('button', { name: '查看MySQL' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看10.0.0.1' }));
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'mysql');
    fireEvent.click(screen.getByRole('button', { name: '选择场景中的 MySQL' }));
    fireEvent.click(screen.getByText('2.5D 俯视'));
    expect(screen.getByTestId('scene')).toHaveAttribute('data-mode', '2.5d');
    fireEvent.click(screen.getByText('2D 平面'));
    const plan = screen.getByTestId('plan');
    expect(plan).toBeVisible();
    expect(screen.getByTestId('plan')).toHaveAttribute('data-zone', 'vm');
    expect(screen.getByTestId('plan')).toHaveAttribute(
      'data-selected',
      'mysql',
    );
    fireEvent.click(screen.getByRole('button', { name: '切换深色主题' }));
    expect(screen.getByTestId('cluster-topology')).toHaveAttribute(
      'data-theme',
      'dark',
    );
    fireEvent.click(screen.getByText('3D 立体'));
    expect(plan).not.toBeVisible();
    fireEvent.click(screen.getByText('2D 平面'));
    expect(screen.getByTestId('plan')).toBe(plan);
    expect(plan).toBeVisible();
    fireEvent.click(screen.getByText('3D 立体'));
    expect(screen.getByTestId('scene')).toHaveAttribute(
      'data-selected',
      'mysql',
    );
    expect(screen.getByTestId('scene')).toHaveAttribute('data-zone', 'vm');
  });

  it('shows IP hosts in the overview, drills through zones and returns with breadcrumbs', async () => {
    await openPage();
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'host-1');
    fireEvent.click(screen.getByRole('button', { name: '进入VM集群分区' }));
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'host-1');
    fireEvent.click(screen.getByRole('button', { name: '查看10.0.0.1' }));
    expect(screen.getByRole('button', { name: '查看MySQL' })).toHaveTextContent(
      '3306/TCP',
    );
    fireEvent.click(screen.getByRole('button', { name: 'VM集群' }));
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'host-1');
    fireEvent.click(screen.getByRole('button', { name: '集群全景' }));
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'host-1');
  });

  it('opens middleware directly from an overview host and preserves its zone', async () => {
    await openPage();
    expect(
      screen.queryByRole('button', { name: '查看MySQL' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看10.0.0.1' }));
    expect(screen.getByTestId('scene')).toHaveAttribute('data-zone', 'vm');
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'mysql');
    expect(screen.getByRole('button', { name: 'VM集群' })).toBeVisible();
  });

  it('finds a service through its cluster and host and follows a changed host category on refresh', async () => {
    await openPage();
    fireEvent.change(
      screen.getByRole('textbox', { name: '搜索服务、主机或 IP' }),
      { target: { value: 'MySQL' } },
    );
    expect(screen.getByRole('button', { name: '查看10.0.0.1' })).toBeVisible();
    expect(
      screen.queryByRole('button', { name: '查看Doris集群' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '进入VM集群分区' }));
    fireEvent.click(screen.getByRole('button', { name: '查看10.0.0.1' }));
    vi.mocked(loadTopology).mockResolvedValue({
      ...snapshot,
      nodes: snapshot.nodes.map((node) => ({ ...node, zone: 'other' })),
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '刷新资源' }));
    });
    expect(screen.getByTestId('scene')).toHaveAttribute('data-zone', 'other');
    expect(screen.getByTestId('scene')).toHaveAttribute('data-nodes', 'mysql');
  });

  it('refreshes after five minutes and visibly retains the previous snapshot on failure', async () => {
    vi.useFakeTimers();
    await openPage();
    fireEvent.click(screen.getByRole('button', { name: '进入VM集群分区' }));
    fireEvent.click(screen.getByRole('button', { name: '查看10.0.0.1' }));
    expect(loadTopology).toHaveBeenCalledTimes(1);
    vi.mocked(loadTopology).mockRejectedValueOnce(new Error('主机接口不可用'));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(299999);
    });
    expect(loadTopology).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(loadTopology).toHaveBeenCalledTimes(2);
    expect(screen.getByText('刷新失败，当前保留上次成功快照')).toBeVisible();
    expect(screen.getByRole('button', { name: '查看MySQL' })).toBeVisible();
    expect(screen.getByText('主机接口不可用')).toBeVisible();
  });

  it('hides empty navigation zones and returns to overview when the focused zone becomes empty', async () => {
    await openPage();
    expect(
      screen.queryByRole('button', { name: '进入Doris集群分区' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '进入VM集群分区' }));
    vi.mocked(loadTopology).mockResolvedValue({ ...snapshot, nodes: [] });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '刷新资源' }));
    });
    expect(
      screen.queryByRole('button', { name: '进入VM集群分区' }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('scene')).not.toHaveAttribute('data-zone');
  });

  it('provides a functional 2D fallback when WebGL fails', async () => {
    await openPage();
    fireEvent.click(screen.getByRole('button', { name: '模拟 WebGL 失败' }));
    expect(screen.getByTestId('plan')).toBeInTheDocument();
    expect(screen.getByText('WebGL 不可用，已切换平面视图。')).toBeVisible();
  });

  it('does not request resources for invalid or inaccessible clusters', async () => {
    routing.clusterId = '-7';
    await openPage();
    expect(listClusters).not.toHaveBeenCalled();
    expect(loadTopology).not.toHaveBeenCalled();
    expect(
      screen.getByText('集群 ID 无效，请从集群列表重新进入。'),
    ).toBeVisible();
    cleanup();
    routing.clusterId = '8';
    await openPage();
    expect(loadTopology).not.toHaveBeenCalled();
    expect(screen.getByText(/未找到该集群/)).toBeVisible();
  });
});
