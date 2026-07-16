import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchDorisLabels,
  queryDorisInstant,
  queryDorisRange,
} from './dorisService';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('Doris metrics service', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset();
  });

  it('does not send an undefined job filter to the metrics endpoints', () => {
    queryDorisInstant({ metric: 'doris_fe_query_total', job: undefined });
    queryDorisRange({
      metric: 'doris_be_memory_allocated_bytes',
      start: 100,
      end: 200,
      step: 15,
      job: undefined,
    });
    fetchDorisLabels('doris_fe_query_total', 7);

    const calls = vi.mocked(request).mock.calls as unknown as Array<
      [string, { params?: Record<string, unknown> }]
    >;
    for (const [, options] of calls) {
      expect(options?.params).not.toHaveProperty('job');
    }
  });
});
