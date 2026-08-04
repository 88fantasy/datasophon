import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LineageOverview from './LineageOverview';
import { getOverview } from './service';

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
}));

vi.mock('./service', () => ({ getOverview: vi.fn() }));

describe('LineageOverview', () => {
  beforeEach(() => {
    vi.mocked(getOverview).mockReset();
  });

  it('renders layer blocks and layer-pair edges, skipping empty layers', async () => {
    vi.mocked(getOverview).mockResolvedValue({
      data: {
        layers: [
          { layer: 'ODS', nodeCount: 10 },
          { layer: 'DWD', nodeCount: 4 },
          { layer: 'ADS', nodeCount: 0 },
        ],
        edges: [{ srcLayer: 'ODS', dstLayer: 'DWD', count: 6 }],
      },
      snapshot: {} as never,
      sourceFreshness: {} as never,
    });

    render(<LineageOverview clusterId={7} />);

    await waitFor(() => expect(getOverview).toHaveBeenCalledWith(7));
    expect(await screen.findByText('ODS')).toBeInTheDocument();
    expect(screen.getByText('DWD')).toBeInTheDocument();
    expect(screen.queryByText('ADS')).not.toBeInTheDocument();
    expect(screen.getByText('ODS → DWD (6)')).toBeInTheDocument();
  });

  it('shows an empty state when every layer has zero nodes', async () => {
    vi.mocked(getOverview).mockResolvedValue({
      data: { layers: [{ layer: 'UNKNOWN', nodeCount: 0 }], edges: [] },
      snapshot: {} as never,
      sourceFreshness: {} as never,
    });

    render(<LineageOverview clusterId={7} />);

    expect(await screen.findByText('暂无分层数据')).toBeInTheDocument();
  });
});
