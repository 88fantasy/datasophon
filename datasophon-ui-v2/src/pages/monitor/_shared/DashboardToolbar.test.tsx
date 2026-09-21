import { act, cleanup, render } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DashboardToolbar from './DashboardToolbar';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));

describe('DashboardToolbar auto refresh', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function mount(refreshInterval: '30s' | 'off' = '30s') {
    const onRefresh = vi.fn();
    const view = render(
      <StrictMode>
        <DashboardToolbar
          timeRange="1h"
          onTimeRangeChange={vi.fn()}
          refreshInterval={refreshInterval}
          onRefreshIntervalChange={vi.fn()}
          onRefresh={onRefresh}
        />
      </StrictMode>,
    );
    return { ...view, onRefresh };
  }

  it('refreshes once per interval under StrictMode and stops after unmount', () => {
    const { onRefresh, unmount } = mount();
    for (let second = 0; second < 30; second++) {
      act(() => vi.advanceTimersByTime(1000));
    }
    expect(onRefresh).toHaveBeenCalledTimes(1);
    unmount();
    act(() => vi.advanceTimersByTime(60_000));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it('pauses in hidden tabs and refreshes once on return', () => {
    const { onRefresh } = mount();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    act(() => vi.advanceTimersByTime(90_000));
    expect(onRefresh).not.toHaveBeenCalled();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    expect(onRefresh).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(30_000));
    expect(onRefresh).toHaveBeenCalledTimes(2);
  });

  it('does not refresh on visibility changes when auto refresh is off', () => {
    const { onRefresh } = mount('off');
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    act(() => vi.advanceTimersByTime(90_000));
    expect(onRefresh).not.toHaveBeenCalled();
  });
});
