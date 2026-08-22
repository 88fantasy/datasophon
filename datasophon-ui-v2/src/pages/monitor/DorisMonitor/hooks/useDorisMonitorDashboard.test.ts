import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { runWithConcurrencyLimit } from '../../_shared/useDashboardData';
import {
  resolveRoleJob,
  useDorisMonitorDashboard,
} from './useDorisMonitorDashboard';

const mocks = vi.hoisted(() => ({
  fetchDorisLabels: vi.fn(),
  useDorisDashboardData: vi.fn(),
}));

vi.mock('../../_shared/dorisService', () => ({
  fetchDorisLabels: mocks.fetchDorisLabels,
}));

vi.mock('../../_shared/useDorisDashboardData', () => ({
  useDorisDashboardData: mocks.useDorisDashboardData,
}));

const DISAGGREGATED_PROFILE = JSON.stringify({
  profile: 'doris-disaggregated',
  roles: {
    fe: ['doris-fe'],
    compute: ['doris-compute'],
  },
});

const renderDisaggregatedDashboard = () =>
  renderHook(
    ({ refreshKey }) =>
      useDorisMonitorDashboard({
        variables: {
          cluster: 'doris',
          feInstance: '.+',
          beInstance: '.+',
          interval: '2m',
        },
        activeSegment: 'cluster',
        timeRange: '1h',
        clusterId: 7,
        refreshKey,
        monitorProfile: DISAGGREGATED_PROFILE,
      }),
    { initialProps: { refreshKey: 0 } },
  );

describe('disaggregated node summary', () => {
  beforeEach(() => {
    mocks.fetchDorisLabels.mockReset();
    mocks.useDorisDashboardData.mockReset();
    mocks.useDorisDashboardData.mockReturnValue({
      instant: {},
      series: {},
      loading: false,
    });
  });

  it('does not treat active reporters as the expected node total', async () => {
    mocks.fetchDorisLabels
      .mockResolvedValueOnce({
        data: { instances: ['fe-1', 'fe-2'], jobs: ['doris-fe'] },
      })
      .mockResolvedValueOnce({
        data: { instances: ['compute-1'], jobs: ['doris-compute'] },
      });

    const { result } = renderDisaggregatedDashboard();

    await waitFor(() => {
      expect(result.current.instant.feAliveCount).toBe(2);
      expect(result.current.instant.beAliveCount).toBe(1);
    });
    expect(result.current.instant.feNodeCount).toBeNaN();
    expect(result.current.instant.beNodeCount).toBeNaN();
  });

  it('keeps one role available when the other labels query fails', async () => {
    mocks.fetchDorisLabels
      .mockResolvedValueOnce({
        data: { instances: ['fe-1'], jobs: ['doris-fe'] },
      })
      .mockRejectedValueOnce(new Error('compute labels unavailable'));

    const { result } = renderDisaggregatedDashboard();

    await waitFor(() => {
      expect(result.current.instant.feAliveCount).toBe(1);
    });
    expect(result.current.instant.feNodeCount).toBeNaN();
    expect(result.current.instant.beNodeCount).toBeNaN();
    expect(result.current.instant.beAliveCount).toBeNaN();
  });

  it('clears a previous active count when refresh labels queries fail', async () => {
    mocks.fetchDorisLabels.mockResolvedValue({
      data: { instances: ['instance-1'], jobs: ['doris'] },
    });
    const { result, rerender } = renderDisaggregatedDashboard();
    await waitFor(() => {
      expect(result.current.instant.feAliveCount).toBe(1);
      expect(result.current.instant.beAliveCount).toBe(1);
    });

    mocks.fetchDorisLabels.mockRejectedValue(new Error('labels unavailable'));
    rerender({ refreshKey: 1 });

    await waitFor(() => {
      expect(result.current.instant.feAliveCount).toBeNaN();
      expect(result.current.instant.beAliveCount).toBeNaN();
    });
  });
});

describe('resolveRoleJob', () => {
  it('profile 里没有该角色的 job（key 缺省）时返回不可用 + 零命中正则', () => {
    expect(resolveRoleJob(undefined)).toEqual({ job: '^$', available: false });
  });

  it('角色 job 列表为空数组时同样视为不可用（而不是退化成全选）', () => {
    expect(resolveRoleJob([])).toEqual({ job: '^$', available: false });
  });

  it('角色有登记 job 时转成匹配正则并标记可用', () => {
    expect(resolveRoleJob(['doris-compute-1'])).toEqual({
      job: 'doris-compute-1',
      available: true,
    });
  });
});

describe('Doris monitor concurrency limiter', () => {
  it('runs dashboard queries without exceeding the configured concurrency', async () => {
    let active = 0;
    let maxActive = 0;

    const tasks = Array.from({ length: 12 }, (_, index) => async () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise((resolve) => setTimeout(resolve, 5));
      active -= 1;
      return index;
    });

    const result = await runWithConcurrencyLimit(tasks, 4);

    expect(result).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
    expect(maxActive).toBeLessThanOrEqual(4);
  });
});
