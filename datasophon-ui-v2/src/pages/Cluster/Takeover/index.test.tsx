import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import {
  listDorisCandidates,
  registerTakeover,
  saveDorisDatasource,
  scanTakeover,
} from '@/services/k8s';
import Takeover from './index';

vi.mock('@/services/k8s', () => ({
  listDorisCandidates: vi.fn(),
  registerTakeover: vi.fn(),
  saveDorisDatasource: vi.fn(),
  scanTakeover: vi.fn(),
  testDorisDatasource: vi.fn(),
}));

vi.mock('@ant-design/pro-components', () => ({
  ProCard: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  // 用最简渲染暴露行数据与选择行为，避免依赖 ProTable 内部结构
  ProTable: ({
    dataSource,
    headerTitle,
    rowKey,
    rowSelection,
  }: {
    dataSource?: Array<Record<string, unknown>>;
    headerTitle?: ReactNode;
    rowKey?: string | ((row: Record<string, unknown>) => string);
    rowSelection?: {
      onChange: (keys: string[]) => void;
      getCheckboxProps?: (row: Record<string, unknown>) => {
        disabled?: boolean;
      };
    };
  }) => (
    <div data-testid="pro-table">
      {headerTitle ? <div data-testid="table-title">{headerTitle}</div> : null}
      {(dataSource ?? []).map((row) => {
        const key =
          typeof rowKey === 'function'
            ? rowKey(row)
            : String(row[rowKey ?? 'releaseName'] ?? row.serviceName);
        return (
          <div
            key={key}
            data-testid="row"
            data-row-key={key}
            data-checkbox-disabled={String(
              rowSelection?.getCheckboxProps?.(row)?.disabled ?? false,
            )}
          >
            {String(row.releaseName ?? row.serviceName)}
            {rowSelection ? (
              <button
                type="button"
                data-testid={`select-${key}`}
                onClick={() => rowSelection.onChange([key])}
              >
                select
              </button>
            ) : null}
          </div>
        );
      })}
      {rowSelection ? (
        <button
          type="button"
          data-testid="clear-selection"
          onClick={() => rowSelection.onChange([])}
        >
          clear
        </button>
      ) : null}
    </div>
  ),
}));

const renderPage = () =>
  render(
    <ClusterContext.Provider
      value={{ clusterId: 8, clusterInfo: {} as DATASOPHON.ClusterInfo }}
    >
      <Takeover />
    </ClusterContext.Provider>,
  );

describe('Takeover', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('warns instead of auto-filling when no candidate is reachable', async () => {
    // 目标集群的真实形态：LoadBalancer 无 ingress，只有集群内地址
    vi.mocked(listDorisCandidates).mockResolvedValue({
      data: [
        {
          serviceName: 'doris-fe',
          namespace: 'doris',
          serviceType: 'ClusterIP',
          host: '192.168.203.136',
          port: 9030,
          source: 'CLUSTER_IP',
          reachable: false,
          hint: '仅集群内地址',
        },
      ],
    } as never);

    renderPage();
    fireEvent.click(screen.getByText('自动发现 Doris 地址'));

    await waitFor(() => {
      expect(screen.getByText('doris-fe')).toBeInTheDocument();
    });
    // 不可达候选不写进表单，避免用户误提交
    const hostInput = screen.getByPlaceholderText(
      '平台可直连的地址，如 10.0.0.9',
    ) as HTMLInputElement;
    expect(hostInput.value).toBe('');
  });

  it('lists matched and pending releases separately after scanning', async () => {
    vi.mocked(saveDorisDatasource).mockResolvedValue({} as never);
    vi.mocked(scanTakeover).mockResolvedValue({
      data: {
        matched: [
          {
            releaseName: 'zookeeper',
            namespace: 'prod',
            chart: 'zookeeper-13.8.7',
            chartName: 'zookeeper',
            frameServiceId: 2,
            frameServiceName: 'zookeeper',
            catalog: 'MIDDLEWARE',
          },
        ],
        pending: [
          {
            releaseName: 'unknown-app',
            namespace: 'prod',
            chart: 'unknown-app-1.0.0',
            chartName: 'unknown-app',
          },
        ],
      },
    } as never);

    renderPage();
    // 跳到第 2 步需要先保存数据源，这里直接填表提交
    fireEvent.change(
      screen.getByPlaceholderText('平台可直连的地址，如 10.0.0.9'),
      { target: { value: '10.0.0.9' } },
    );
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByText('保存并下一步'));

    await waitFor(() => {
      expect(screen.getByText('扫描集群现有服务')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText('扫描集群现有服务'));

    await waitFor(() => {
      expect(screen.getByText('zookeeper')).toBeInTheDocument();
    });
    expect(screen.getByText('已匹配的服务')).toBeInTheDocument();
    expect(screen.getByText('未匹配到框架服务定义')).toBeInTheDocument();
    expect(screen.getByText('unknown-app')).toBeInTheDocument();
    // 已匹配的默认全选
    expect(screen.getByText('接管选中的 1 个服务')).toBeInTheDocument();
  });

  it('disables submit when nothing is selected', async () => {
    vi.mocked(saveDorisDatasource).mockResolvedValue({} as never);
    vi.mocked(scanTakeover).mockResolvedValue({
      data: {
        matched: [
          {
            releaseName: 'apisix',
            namespace: 'apisix',
            chart: 'apisix-2.12.5',
            chartName: 'apisix',
            frameServiceId: 1,
            frameServiceName: 'apisix',
            catalog: 'MIDDLEWARE',
          },
        ],
        pending: [],
      },
    } as never);

    renderPage();
    fireEvent.change(
      screen.getByPlaceholderText('平台可直连的地址，如 10.0.0.9'),
      { target: { value: '10.0.0.9' } },
    );
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByText('保存并下一步'));
    await waitFor(() =>
      expect(screen.getByText('扫描集群现有服务')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByText('扫描集群现有服务'));
    await waitFor(() =>
      expect(screen.getByText('接管选中的 1 个服务')).toBeInTheDocument(),
    );

    fireEvent.click(screen.getAllByTestId('clear-selection')[0]);

    await waitFor(() => {
      const btn = screen.getByText('接管选中的 0 个服务').closest('button');
      expect(btn).toBeDisabled();
    });
    expect(registerTakeover).not.toHaveBeenCalled();
  });

  it('keeps namespace and source kind in deployment identity and register payload', async () => {
    vi.mocked(saveDorisDatasource).mockResolvedValue({} as never);
    vi.mocked(registerTakeover).mockResolvedValue({ data: [] } as never);
    vi.mocked(scanTakeover).mockResolvedValue({
      data: {
        matched: [
          {
            releaseName: 'shared-name',
            namespace: 'helm-ns',
            sourceKind: 'HELM',
            chart: 'shared-1.0.0',
            chartName: 'shared',
            frameServiceId: 1,
            frameServiceName: 'helm-service',
          },
          {
            releaseName: 'shared-name',
            namespace: 'operator-ns',
            sourceKind: 'CR',
            chart: 'doriscluster',
            chartName: 'doriscluster',
            frameServiceId: 2,
            frameServiceName: 'doris',
          },
        ],
        pending: [],
      },
    } as never);

    renderPage();
    fireEvent.change(
      screen.getByPlaceholderText('平台可直连的地址，如 10.0.0.9'),
      { target: { value: '10.0.0.9' } },
    );
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByText('保存并下一步'));
    await waitFor(() =>
      expect(screen.getByText('扫描集群现有服务')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByText('扫描集群现有服务'));
    await waitFor(() =>
      expect(screen.getByText('接管选中的 2 个服务')).toBeInTheDocument(),
    );

    const rows = screen.getAllByTestId('row');
    expect(rows[0]).toHaveAttribute(
      'data-row-key',
      'HELM:helm-ns/shared-name',
    );
    expect(rows[1]).toHaveAttribute(
      'data-row-key',
      'CR:operator-ns/shared-name',
    );
    fireEvent.click(screen.getAllByTestId('clear-selection')[0]);
    fireEvent.click(screen.getByTestId('select-CR:operator-ns/shared-name'));
    fireEvent.click(screen.getByText('接管选中的 1 个服务'));

    await waitFor(() => {
      expect(registerTakeover).toHaveBeenCalledWith(8, [
        {
          releaseName: 'shared-name',
          namespace: 'operator-ns',
          frameServiceId: 2,
          sourceKind: 'CR',
        },
      ]);
    });
  });
});

describe('Takeover rescan reconciliation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(saveDorisDatasource).mockResolvedValue({} as never);
  });

  const gotoScanStep = async () => {
    renderPage();
    fireEvent.change(
      screen.getByPlaceholderText('平台可直连的地址，如 10.0.0.9'),
      { target: { value: '10.0.0.9' } },
    );
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByText('保存并下一步'));
    await waitFor(() =>
      expect(screen.getByText('扫描集群现有服务')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByText('扫描集群现有服务'));
  };

  it('does not re-select or allow re-checking already registered releases', async () => {
    vi.mocked(scanTakeover).mockResolvedValue({
      data: {
        matched: [
          {
            releaseName: 'zookeeper',
            namespace: 'prod',
            chart: 'zookeeper-13.8.7',
            chartName: 'zookeeper',
            frameServiceId: 2,
            frameServiceName: 'zookeeper',
            catalog: 'MIDDLEWARE',
            registered: true,
          },
          {
            releaseName: 'kyuubi',
            namespace: 'spark',
            chart: 'kyuubi-0.1.0',
            chartName: 'kyuubi',
            frameServiceId: 3,
            frameServiceName: 'kyuubi',
            catalog: 'MIDDLEWARE',
            registered: false,
          },
        ],
        pending: [],
        missing: [],
      },
    } as never);

    await gotoScanStep();

    // 只有未接管的那个进默认选中
    await waitFor(() =>
      expect(screen.getByText('接管选中的 1 个服务')).toBeInTheDocument(),
    );
    const rows = screen.getAllByTestId('row');
    expect(rows[0]).toHaveAttribute('data-checkbox-disabled', 'true');
    expect(rows[1]).toHaveAttribute('data-checkbox-disabled', 'false');
  });

  it('warns about registered instances whose release vanished', async () => {
    vi.mocked(scanTakeover).mockResolvedValue({
      data: {
        matched: [],
        pending: [],
        missing: [
          {
            instanceId: 102,
            releaseName: 'kyuubi',
            namespace: 'spark',
            serviceName: 'kyuubi',
          },
        ],
      },
    } as never);

    await gotoScanStep();

    await waitFor(() => {
      expect(
        screen.getByText('有 1 个已接管的服务在集群中已不存在'),
      ).toBeInTheDocument();
    });
    expect(screen.getByText(/spark\/kyuubi/)).toBeInTheDocument();
  });
});
