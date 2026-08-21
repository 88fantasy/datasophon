import { describe, expect, it } from 'vitest';
import { runWithConcurrencyLimit } from '../../_shared/useDashboardData';
import { resolveRoleJob } from './useDorisMonitorDashboard';

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
