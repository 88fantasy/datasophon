import { useEffect, useMemo, useState } from 'react';
import { fetchDorisLabels } from '../../_shared/dorisService';
import { TIME_RANGE_SECONDS } from '../../_shared/panelTypes';
import type { TimeSeriesPoint } from '../../_shared/types';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import {
  PANEL_QUERIES,
  type JuiceFSPanelDescriptor,
} from '../panelQueries';

export interface JuiceFSDashboardVariables {
  name: string;
}

export interface JuiceFSInstantValues {
  uptime: number;
  dataSize: number;
  files: number;
  clientSessions: number;
  cacheHitPercent: number;
  stagingBlocks: number;
}

export interface JuiceFSDashboardData {
  instant: JuiceFSInstantValues;
  series: Record<string, TimeSeriesPoint[]>;
  volumes: string[];
  loading: boolean;
  rateInterval: string;
  error?: string;
}

const ALL_PANEL_IDS = Array.from(
  { length: 17 },
  (_, i) => `J${String(i + 1).padStart(2, '0')}`,
);

function intervalToPromDuration(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600}h`;
  if (seconds % 60 === 0) return `${seconds / 60}m`;
  return `${seconds}s`;
}

function calcRateInterval(rangeSeconds: number): string {
  const scrapeIntervalSeconds = 15;
  const intervalSeconds = Math.max(
    scrapeIntervalSeconds * 4,
    Math.floor(rangeSeconds / 200),
  );
  return intervalToPromDuration(intervalSeconds);
}

function withVolumeFilter(
  descriptors: Record<string, JuiceFSPanelDescriptor>,
  volume: string,
): Record<string, JuiceFSPanelDescriptor> {
  if (!volume) return descriptors;
  return Object.fromEntries(
    Object.entries(descriptors).map(([id, descriptor]) => {
      if (descriptor.type === 'node-count') return [id, descriptor];
      if (descriptor.type === 'instant') {
        return [
          id,
          {
            ...descriptor,
            filters: { ...descriptor.filters, vol_name: volume },
          },
        ];
      }
      return [
        id,
        {
          ...descriptor,
          queries: descriptor.queries.map((query) => ({
            ...query,
            filters: { ...query.filters, vol_name: volume },
            denominatorFilters: query.denominatorMetric
              ? { ...query.denominatorFilters, vol_name: volume }
              : query.denominatorFilters,
          })),
        },
      ];
    }),
  );
}

function combineRatioSeries(
  points: TimeSeriesPoint[],
  hitPrefix: string,
  missPrefix: string,
  outputPrefix: string,
): TimeSeriesPoint[] {
  const missesBySeries = new Map<string, Map<number, number>>();
  for (const point of points) {
    if (!point.series.startsWith(missPrefix)) continue;
    const suffix = point.series.slice(missPrefix.length);
    const byTime = missesBySeries.get(suffix) ?? new Map<number, number>();
    byTime.set(point.time, point.value);
    missesBySeries.set(suffix, byTime);
  }

  return points
    .filter((point) => point.series.startsWith(hitPrefix))
    .map((point) => {
      const suffix = point.series.slice(hitPrefix.length);
      const miss = missesBySeries.get(suffix)?.get(point.time) ?? 0;
      const total = point.value + miss;
      const value = total > 0 ? (point.value / total) * 100 : 0;
      return {
        time: point.time,
        value: Number.isNaN(value) ? 0 : value,
        series: `${outputPrefix}${suffix}`,
      };
    });
}

function lastValue(points: TimeSeriesPoint[]): number {
  if (points.length === 0) return 0;
  const sorted = [...points].sort((a, b) => a.time - b.time);
  const value = sorted[sorted.length - 1].value;
  return Number.isFinite(value) ? value : 0;
}

export interface UseJuiceFSDashboardParams {
  variables: JuiceFSDashboardVariables;
  timeRange: string;
  clusterId?: number;
  refreshKey: number;
}

export function useJuiceFSDashboard({
  variables,
  timeRange,
  clusterId = 1,
  refreshKey,
}: UseJuiceFSDashboardParams): JuiceFSDashboardData {
  const [volumes, setVolumes] = useState<string[]>([]);

  const rateInterval = useMemo(() => {
    const rangeSeconds = TIME_RANGE_SECONDS[timeRange] ?? 3600;
    return calcRateInterval(rangeSeconds);
  }, [timeRange]);

  useEffect(() => {
    fetchDorisLabels('juicefs_uptime', clusterId)
      .then((res) => {
        const volumeValues = res?.data?.attributes?.vol_name ?? [];
        setVolumes(volumeValues);
      })
      .catch(() => {
        setVolumes([]);
      });
  }, [clusterId, refreshKey]);

  const descriptors = useMemo(
    () => withVolumeFilter(PANEL_QUERIES, variables.name),
    [variables.name],
  );

  const data = useDorisDashboardData({
    panelDescriptors: descriptors,
    panelIds: ALL_PANEL_IDS,
    instance: '.+',
    job: '.+',
    timeRange,
    clusterId,
    refreshKey,
  });

  const cacheHitSeries = useMemo(
    () => combineRatioSeries(data.series.J05 ?? [], 'Hits', 'Miss', 'Hit %'),
    [data.series.J05],
  );

  const cacheRatioSeries = useMemo(
    () => [
      ...combineRatioSeries(
        data.series.J15 ?? [],
        'Count Hits',
        'Count Miss',
        'By Count',
      ),
      ...combineRatioSeries(
        data.series.J15 ?? [],
        'Bytes Hits',
        'Bytes Miss',
        'By Bytes',
      ),
    ],
    [data.series.J15],
  );

  return {
    instant: {
      uptime: data.instant.J01 ?? 0,
      dataSize: data.instant.J02 ?? 0,
      files: data.instant.J03 ?? 0,
      clientSessions: data.instant.J04 ?? 0,
      cacheHitPercent: lastValue(cacheHitSeries),
      stagingBlocks: data.instant.J06 ?? 0,
    },
    series: {
      ...data.series,
      J15: cacheRatioSeries,
    },
    volumes,
    rateInterval,
    loading: data.loading,
    error: data.error,
  };
}
