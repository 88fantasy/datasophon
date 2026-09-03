import { useCallback, useEffect, useRef, useState } from 'react';

export interface UseActiveTaskPollingOptions<T> {
  fetcher: () => Promise<T>;
  active?: boolean;
  intervalMs?: number;
}

export interface ActiveTaskPollingResult<T> {
  data?: T;
  error?: unknown;
  loading: boolean;
  lastUpdatedAt?: number;
  refresh: () => void;
}

const DEFAULT_INTERVAL_MS = 10_000;

export function useActiveTaskPolling<T>({
  fetcher,
  active = true,
  intervalMs = DEFAULT_INTERVAL_MS,
}: UseActiveTaskPollingOptions<T>): ActiveTaskPollingResult<T> {
  const fetcherRef = useRef(fetcher);
  const inFlightRef = useRef(false);
  const mountedRef = useRef(true);
  const [data, setData] = useState<T>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<number>();

  fetcherRef.current = fetcher;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const refresh = useCallback(() => {
    if (!active || document.hidden || inFlightRef.current) return;

    inFlightRef.current = true;
    setLoading(true);
    setError(undefined);
    void fetcherRef
      .current()
      .then((value) => {
        if (!mountedRef.current) return;
        setData(value);
        setLastUpdatedAt(Date.now());
      })
      .catch((requestError: unknown) => {
        if (mountedRef.current) setError(requestError);
      })
      .finally(() => {
        inFlightRef.current = false;
        if (mountedRef.current) setLoading(false);
      });
  }, [active]);

  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | undefined;

    const stop = () => {
      if (timer === undefined) return;
      clearInterval(timer);
      timer = undefined;
    };

    const start = () => {
      if (!active || document.hidden || timer !== undefined) return;
      refresh();
      timer = setInterval(refresh, intervalMs);
    };

    const handleVisibilityChange = () => {
      if (document.hidden || !active) stop();
      else start();
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    start();
    return () => {
      stop();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [active, intervalMs, refresh]);

  return { data, error, loading, lastUpdatedAt, refresh };
}
