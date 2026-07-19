import { useEffect, useMemo, useState } from 'react';
import type { PrometheusMatrix } from '../../_shared/charts/promql';
import { fetchDorisLabels, queryDorisRange } from '../../_shared/dorisService';
import { TIME_RANGE_SECONDS } from '../../_shared/panelTypes';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  PANEL_QUERIES,
  VALKEY_JOB_FILTER,
  VALKEY_KEY_EXPIRING_QUERY,
  VALKEY_KEY_TOTAL_QUERY,
  type ValkeyDashboardVariables,
} from '../panelQueries';

export interface ValkeyInstantValues {
  maxUptime: number;
  clients: number;
  memoryUsagePct: number;
  memoryMaxBytes: number;
  cacheHitPct: number;
}

export interface ValkeyDashboardData {
  instant: ValkeyInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  instances: string[];
  loading: boolean;
  error?: string;
}

const QUERY_PANEL_IDS = [
  'V01',
  'V02',
  'V03',
  'V03_max',
  'V04',
  'V05',
  'V06',
  'V07',
  'V08',
  'V09',
  'V10',
  'V11',
  'V13',
  'V14',
];

interface PairedKeyPoint {
  instance: string;
  job: string;
  timestamp: number;
  total: number;
  expiring: number;
}

/**
 * Pair DB key gauges by instance/job/db/timestamp, then aggregate every DB for
 * each Valkey instance. Keeping the raw matrices avoids losing label identity
 * before the subtraction is performed.
 */
export function deriveKeyExpirationSeries(
  totalMatrix: PrometheusMatrix,
  expiringMatrix: PrometheusMatrix,
): TimeSeriesPoint[] {
  const pairs = new Map<string, PairedKeyPoint>();

  const addMatrix = (matrix: PrometheusMatrix, field: 'total' | 'expiring') => {
    for (const row of matrix.result) {
      const instance = row.metric.instance ?? '';
      const job = row.metric.job ?? '';
      const db = row.metric.db ?? '';
      for (const [timestamp, rawValue] of row.values) {
        const key = JSON.stringify([instance, job, db, timestamp]);
        const point = pairs.get(key) ?? {
          instance,
          job,
          timestamp,
          total: 0,
          expiring: 0,
        };
        point[field] += Number.parseFloat(rawValue);
        pairs.set(key, point);
      }
    }
  };

  addMatrix(totalMatrix, 'total');
  addMatrix(expiringMatrix, 'expiring');

  const byInstance = new Map<string, PairedKeyPoint>();
  for (const point of pairs.values()) {
    const key = JSON.stringify([point.instance, point.job, point.timestamp]);
    const aggregate = byInstance.get(key) ?? {
      instance: point.instance,
      job: point.job,
      timestamp: point.timestamp,
      total: 0,
      expiring: 0,
    };
    aggregate.total += Math.max(point.total - point.expiring, 0);
    aggregate.expiring += point.expiring;
    byInstance.set(key, aggregate);
  }

  const identities = new Set(
    [...byInstance.values()].map((point) =>
      JSON.stringify([point.instance, point.job]),
    ),
  );
  const withIdentity = identities.size > 1;
  const points: TimeSeriesPoint[] = [];
  for (const point of byInstance.values()) {
    const identity = [point.instance, point.job].filter(Boolean).join(' / ');
    const suffix = withIdentity && identity ? ` (${identity})` : '';
    points.push(
      {
        time: point.timestamp * 1000,
        value: point.total,
        series: `Not-Expiring${suffix}`,
      },
      {
        time: point.timestamp * 1000,
        value: point.expiring,
        series: `Expiring${suffix}`,
      },
    );
  }
  return points.sort(
    (left, right) =>
      left.time - right.time || left.series.localeCompare(right.series),
  );
}

/** Calculate V04 from the latest five minutes of Doris rate buckets. */
export function calculateCacheHitPct(points: TimeSeriesPoint[]): number {
  const latestTime = Math.max(...points.map((point) => point.time));
  if (!Number.isFinite(latestTime)) return 0;
  const windowStart = latestTime - 5 * 60 * 1000;

  let hits = 0;
  let misses = 0;
  for (const point of points) {
    if (point.time < windowStart) continue;
    if (point.series === 'Hits' || point.series.startsWith('Hits (')) {
      hits += point.value;
    } else if (
      point.series === 'Misses' ||
      point.series.startsWith('Misses (')
    ) {
      misses += point.value;
    }
  }
  const total = hits + misses;
  return total > 0 ? (hits / total) * 100 : 0;
}

export interface UseValkeyDashboardParams {
  variables: ValkeyDashboardVariables;
  timeRange: string;
  clusterId: number;
  refreshKey: number;
}

export function useValkeyDashboard({
  variables,
  timeRange,
  clusterId,
  refreshKey,
}: UseValkeyDashboardParams): ValkeyDashboardData {
  const [instances, setInstances] = useState<string[]>([]);
  const [keyExpirationSeries, setKeyExpirationSeries] = useState<
    TimeSeriesPoint[]
  >([]);
  const [keyExpirationLoading, setKeyExpirationLoading] = useState(true);

  useEffect(() => {
    if (clusterId <= 0) {
      setInstances([]);
      return;
    }
    fetchDorisLabels('redis_up', clusterId, VALKEY_JOB_FILTER)
      .then((res) => setInstances(res?.data?.instances ?? []))
      .catch(() => setInstances([]));
  }, [clusterId, refreshKey]);

  useEffect(() => {
    let cancelled = false;
    if (clusterId <= 0) {
      setKeyExpirationSeries([]);
      setKeyExpirationLoading(false);
      return () => {
        cancelled = true;
      };
    }

    setKeyExpirationLoading(true);
    const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
    const end = Math.floor(Date.now() / 1000);
    const start = end - rangeSeconds;
    const step = Math.max(15, Math.floor(rangeSeconds / 200));
    const commonParams = {
      instance: variables.instance,
      job: VALKEY_JOB_FILTER,
      start,
      end,
      step,
      clusterId,
    };

    Promise.all([
      queryDorisRange({
        ...commonParams,
        metric: VALKEY_KEY_TOTAL_QUERY.metric,
        groupBy: VALKEY_KEY_TOTAL_QUERY.groupBy,
      }),
      queryDorisRange({
        ...commonParams,
        metric: VALKEY_KEY_EXPIRING_QUERY.metric,
        groupBy: VALKEY_KEY_EXPIRING_QUERY.groupBy,
      }),
    ])
      .then(([total, expiring]) => {
        if (cancelled) return;
        setKeyExpirationSeries(
          deriveKeyExpirationSeries(
            total?.data ?? { resultType: 'matrix', result: [] },
            expiring?.data ?? { resultType: 'matrix', result: [] },
          ),
        );
      })
      .catch(() => {
        if (!cancelled) setKeyExpirationSeries([]);
      })
      .finally(() => {
        if (!cancelled) setKeyExpirationLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [variables.instance, timeRange, clusterId, refreshKey]);

  const data = useDorisDashboardData({
    panelDescriptors: PANEL_QUERIES,
    panelIds: QUERY_PANEL_IDS,
    instance: variables.instance,
    job: VALKEY_JOB_FILTER,
    timeRange,
    clusterId,
    refreshKey,
  });

  const memoryMaxBytes = Number.isFinite(data.instant.V03_max)
    ? data.instant.V03_max
    : 0;

  const memorySeries = useMemo(() => {
    const raw = data.series.V09 ?? [];
    return memoryMaxBytes <= 0
      ? raw.filter(
          (point) =>
            point.series !== 'Max' && !point.series.startsWith('Max ('),
        )
      : raw;
  }, [data.series.V09, memoryMaxBytes]);

  const rejectedOrEvictedSeries = useMemo(() => {
    const rejected = data.series.V14 ?? [];
    return rejected.length > 0
      ? rejected
      : (data.series.V13 ?? []).filter(
          (point) =>
            point.series === 'Evicted' || point.series.startsWith('Evicted ('),
        );
  }, [data.series.V14, data.series.V13]);

  return {
    instant: {
      maxUptime: data.instant.V01 ?? 0,
      clients: data.instant.V02 ?? 0,
      memoryUsagePct:
        memoryMaxBytes <= 0 || !Number.isFinite(data.instant.V03)
          ? -1
          : data.instant.V03,
      memoryMaxBytes,
      cacheHitPct: calculateCacheHitPct(data.series.V04 ?? []),
    },
    series: {
      ...data.series,
      V09: memorySeries,
      V12: keyExpirationSeries,
      V14: rejectedOrEvictedSeries,
    },
    instances,
    loading: data.loading || keyExpirationLoading,
    error: data.error,
  };
}
