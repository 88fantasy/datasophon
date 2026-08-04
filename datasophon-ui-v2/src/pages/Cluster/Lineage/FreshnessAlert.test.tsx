import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FreshnessAlert from './FreshnessAlert';
import { rebuild } from './service';

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

vi.mock('./service', () => ({ rebuild: vi.fn() }));

describe('FreshnessAlert', () => {
  beforeEach(() => {
    vi.mocked(rebuild).mockReset();
  });

  it('renders info state when the snapshot is fresh', () => {
    render(
      <FreshnessAlert
        clusterId={7}
        snapshot={{
          generation: 3,
          targetGeneration: 3,
          builtAt: new Date().toISOString(),
          ageSeconds: 5,
          stale: false,
          lastRebuildError: null,
        }}
        sourceFreshness={{ lastEventReceivedAt: null, status: 'OK' }}
      />,
    );
    expect(screen.queryByText(/快照已过期/)).not.toBeInTheDocument();
    expect(screen.queryByText(/上次重建失败/)).not.toBeInTheDocument();
  });

  it('escalates to error styling and shows the failure reason when rebuild last failed', () => {
    render(
      <FreshnessAlert
        clusterId={7}
        snapshot={{
          generation: 3,
          targetGeneration: 5,
          builtAt: new Date().toISOString(),
          ageSeconds: 900,
          stale: true,
          lastRebuildError: 'boom',
        }}
        sourceFreshness={{ lastEventReceivedAt: null, status: 'NO_DATA' }}
      />,
    );
    expect(screen.getByText(/快照已过期/)).toBeInTheDocument();
    expect(screen.getByText(/上次重建失败：boom/)).toBeInTheDocument();
    expect(screen.getByText(/尚未收到任何血缘事件/)).toBeInTheDocument();
  });

  it('triggers a manual rebuild and notifies the parent', async () => {
    vi.mocked(rebuild).mockResolvedValue({ generation: 4 });
    const onRebuilt = vi.fn();
    render(
      <FreshnessAlert
        clusterId={7}
        snapshot={{
          generation: 3,
          targetGeneration: 3,
          builtAt: new Date().toISOString(),
          ageSeconds: 5,
          stale: false,
          lastRebuildError: null,
        }}
        sourceFreshness={{ lastEventReceivedAt: null, status: 'OK' }}
        onRebuilt={onRebuilt}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '立即重建' }));

    await waitFor(() => expect(rebuild).toHaveBeenCalledWith(7));
    await waitFor(() => expect(onRebuilt).toHaveBeenCalled());
  });
});
