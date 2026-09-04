import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useActiveTaskPolling } from './useActiveTaskPolling';

describe('useActiveTaskPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T00:00:00.000Z'));
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: false,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads initially and supports a manual refresh with an update timestamp', async () => {
    const fetcher = vi
      .fn<() => Promise<string>>()
      .mockResolvedValueOnce('first')
      .mockResolvedValueOnce('second');
    const { result } = renderHook(() =>
      useActiveTaskPolling({ fetcher, intervalMs: 10_000 }),
    );

    await act(async () => {});
    expect(result.current.data).toBe('first');
    expect(result.current.lastUpdatedAt).toBe(
      Date.parse('2026-09-03T00:00:00.000Z'),
    );

    act(() => result.current.refresh());
    await act(async () => {});
    expect(result.current.data).toBe('second');
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('refreshes every ten seconds but does not overlap an in-flight request', async () => {
    let resolve: ((value: string) => void) | undefined;
    const fetcher = vi.fn(
      () =>
        new Promise<string>((done) => {
          resolve = done;
        }),
    );
    renderHook(() => useActiveTaskPolling({ fetcher }));

    expect(fetcher).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(30_000));
    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => resolve?.('done'));
    act(() => vi.advanceTimersByTime(10_000));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('does not poll until a manual refresh when auto refresh is disabled', async () => {
    const fetcher = vi
      .fn<() => Promise<string>>()
      .mockResolvedValue('snapshot');
    const { result } = renderHook(() =>
      useActiveTaskPolling({ fetcher, autoRefresh: false }),
    );

    await act(async () => {});
    expect(fetcher).not.toHaveBeenCalled();

    act(() => result.current.refresh());
    await act(async () => {});
    expect(result.current.data).toBe('snapshot');
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('stops while hidden or inactive, preserves the snapshot, and resumes when visible', async () => {
    const fetcher = vi
      .fn<() => Promise<string>>()
      .mockResolvedValue('snapshot');
    const { result, rerender, unmount } = renderHook(
      ({ active }) => useActiveTaskPolling({ fetcher, active }),
      { initialProps: { active: true } },
    );
    await act(async () => {});
    expect(result.current.data).toBe('snapshot');

    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: true,
    });
    document.dispatchEvent(new Event('visibilitychange'));
    act(() => vi.advanceTimersByTime(30_000));
    expect(fetcher).toHaveBeenCalledTimes(1);

    rerender({ active: false });
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: false,
    });
    document.dispatchEvent(new Event('visibilitychange'));
    act(() => vi.advanceTimersByTime(30_000));
    expect(result.current.data).toBe('snapshot');
    expect(fetcher).toHaveBeenCalledTimes(1);

    rerender({ active: true });
    await act(async () => {});
    expect(fetcher).toHaveBeenCalledTimes(2);
    unmount();
    act(() => vi.advanceTimersByTime(30_000));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('exposes request errors without discarding the previous snapshot', async () => {
    const fetcher = vi
      .fn<() => Promise<string>>()
      .mockResolvedValueOnce('snapshot')
      .mockRejectedValueOnce(new Error('unavailable'));
    const { result } = renderHook(() => useActiveTaskPolling({ fetcher }));
    await act(async () => {});
    act(() => result.current.refresh());
    await act(async () => {});

    expect(result.current.data).toBe('snapshot');
    expect(result.current.error).toBeInstanceOf(Error);
  });
});
