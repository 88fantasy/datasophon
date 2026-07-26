import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listAlertHistory } from '@/services/alarm';
import HistoryTab from './History';

type TableRequest = (params: Record<string, unknown>) => Promise<{
  data: DATASOPHON.AlertHistoryResponse[];
  success: boolean;
  total: number;
}>;

interface TableColumn {
  dataIndex?: string;
  search?: {
    transform?: (value: string[]) => Record<string, string>;
  };
}

const tableMock = vi.hoisted(() => ({
  request: undefined as TableRequest | undefined,
  columns: undefined as TableColumn[] | undefined,
}));

vi.mock('@ant-design/pro-components', () => ({
  ProTable: (props: { columns: TableColumn[]; request: TableRequest }) => {
    tableMock.request = props.request;
    tableMock.columns = props.columns;
    return <div data-testid="history-table" />;
  },
}));

vi.mock('@/services/alarm', () => ({
  listAlertHistory: vi.fn(),
}));

describe('HistoryTab', () => {
  beforeEach(() => {
    tableMock.request = undefined;
    tableMock.columns = undefined;
    vi.mocked(listAlertHistory).mockReset();
    vi.mocked(listAlertHistory).mockResolvedValue({
      data: {
        totalList: [
          {
            id: 1,
            alertGroupName: 'hdfs',
            alertTargetName: 'NameNode Survive',
            alertInfo: 'NameNode unavailable',
            alertAdvice: 'Check process',
            hostname: 'node-1',
            alertLevel: 'exception',
            alertLevelCode: 2,
            status: 'firing',
            statusCode: 1,
            createTime: '2026-07-26 10:00:00',
          },
        ],
        totalCount: 1,
      },
    });
  });

  it('maps table filters and pagination to the history API', async () => {
    render(<HistoryTab clusterId={7} />);

    const result = await tableMock.request?.({
      current: 2,
      pageSize: 50,
      alertTargetName: 'NameNode',
      hostname: 'node-1',
      alertLevel: 2,
      status: 1,
      startTime: '2026-07-01 00:00:00',
      endTime: '2026-07-26 23:59:59',
    });

    expect(listAlertHistory).toHaveBeenCalledWith(7, {
      alertTargetName: 'NameNode',
      hostname: 'node-1',
      alertLevel: 2,
      status: 1,
      startTime: '2026-07-01 00:00:00',
      endTime: '2026-07-26 23:59:59',
      page: 2,
      pageSize: 50,
    });
    expect(result).toEqual({
      data: [expect.objectContaining({ id: 1 })],
      success: true,
      total: 1,
    });
  });

  it('transforms the alert time range into backend query parameters', () => {
    render(<HistoryTab clusterId={7} />);

    const timeColumn = tableMock.columns?.find(
      (column) => column.dataIndex === 'alertTimeRange',
    );

    expect(
      timeColumn?.search?.transform?.([
        '2026-07-01 00:00:00',
        '2026-07-26 23:59:59',
      ]),
    ).toEqual({
      startTime: '2026-07-01 00:00:00',
      endTime: '2026-07-26 23:59:59',
    });
  });
});
