import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import LineageTableList from './index';
import type { NodeMeta } from './service';
import { getOverview, listTables } from './service';

const { historyPush } = vi.hoisted(() => ({ historyPush: vi.fn() }));

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({
      id,
      defaultMessage,
    }: {
      id: string;
      defaultMessage?: string;
    }) => defaultMessage ?? id,
  }),
  history: { push: historyPush },
}));

type TableRequest = (params: Record<string, unknown>) => Promise<{
  data: NodeMeta[];
  success: boolean;
  total: number;
}>;

interface TableColumn {
  dataIndex?: string;
  key?: string;
  render?: (value: unknown, record: NodeMeta) => React.ReactNode;
}

const tableMock = vi.hoisted(() => ({
  request: undefined as TableRequest | undefined,
  columns: undefined as TableColumn[] | undefined,
}));

vi.mock('@ant-design/pro-components', () => ({
  ProTable: (props: { columns: TableColumn[]; request: TableRequest }) => {
    tableMock.request = props.request;
    tableMock.columns = props.columns;
    return <div data-testid="lineage-table" />;
  },
}));

vi.mock('./service', () => ({
  listTables: vi.fn(),
  getOverview: vi.fn(),
}));

function renderPage() {
  return render(
    <ClusterContext.Provider value={{ clusterId: 7, clusterInfo: {} } as never}>
      <LineageTableList />
    </ClusterContext.Provider>,
  );
}

describe('Lineage table list page', () => {
  beforeEach(() => {
    historyPush.mockReset();
    tableMock.request = undefined;
    tableMock.columns = undefined;
    vi.mocked(listTables).mockReset();
    vi.mocked(getOverview).mockReset();
    vi.mocked(getOverview).mockResolvedValue({
      data: { layers: [], edges: [] },
      snapshot: {} as never,
      sourceFreshness: {} as never,
    });
  });

  it('maps ProTable params to listTables and surfaces freshness from the response', async () => {
    vi.mocked(listTables).mockResolvedValue({
      data: {
        list: [
          {
            id: 42,
            clusterId: 7,
            connector: 'hive',
            catalogName: 'hive_catalog',
            databaseName: 'ods',
            tableName: 'orders',
            canonicalName: 'hive.ods.orders',
            dwLayer: 'ODS',
          },
        ],
        total: 1,
      },
      snapshot: {
        generation: 3,
        targetGeneration: 3,
        builtAt: new Date().toISOString(),
        ageSeconds: 5,
        stale: false,
        lastRebuildError: null,
      },
      sourceFreshness: { lastEventReceivedAt: null, status: 'OK' },
    });

    renderPage();
    await screen.findByTestId('lineage-table');

    const result = await tableMock.request?.({
      current: 2,
      pageSize: 50,
      keyword: 'orders',
      layer: 'ODS',
    });

    expect(listTables).toHaveBeenCalledWith({
      clusterId: 7,
      page: 2,
      size: 50,
      keyword: 'orders',
      layer: 'ODS',
      connector: undefined,
      database: undefined,
    });
    expect(result).toEqual({
      data: [expect.objectContaining({ canonicalName: 'hive.ods.orders' })],
      total: 1,
      success: true,
    });

    // 新鲜度信息来自表清单响应本身，不是独立请求
    await waitFor(() =>
      expect(screen.getByText(/快照构建于/)).toBeInTheDocument(),
    );
  });

  it('navigates to the graph detail page when the canonical-name column is clicked', () => {
    renderPage();
    const nameColumn = tableMock.columns?.find(
      (column) => column.key === 'canonicalName',
    );
    const record: NodeMeta = {
      id: 42,
      clusterId: 7,
      connector: 'hive',
      catalogName: 'hive_catalog',
      databaseName: 'ods',
      tableName: 'orders',
      canonicalName: 'hive.ods.orders',
      dwLayer: 'ODS',
    };
    const rendered = nameColumn?.render?.(
      undefined,
      record,
    ) as React.ReactElement<{
      onClick: () => void;
    }>;
    rendered.props.onClick();
    expect(historyPush).toHaveBeenCalledWith('/cluster/7/lineage/42');
  });
});
