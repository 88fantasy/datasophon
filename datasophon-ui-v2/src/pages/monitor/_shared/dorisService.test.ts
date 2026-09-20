import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchDorisLabels,
  fetchDorisNodeCount,
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
      expect(options).not.toHaveProperty('skipErrorHandler');
    }
  });
});

it('opts into local feedback without sending the request option as a query filter or swallowing rejection', async () => {
  const error = new Error('offline');
  vi.mocked(request).mockRejectedValue(error);
  await expect(
    queryDorisInstant({ metric: 'cpu' }, { skipErrorHandler: true }),
  ).rejects.toBe(error);
  await expect(
    queryDorisRange(
      { metric: 'cpu', start: 1, end: 2, step: 1 },
      { skipErrorHandler: true },
    ),
  ).rejects.toBe(error);
  await expect(
    fetchDorisNodeCount('worker', 1, { skipErrorHandler: true }),
  ).rejects.toBe(error);
  const calls = vi.mocked(request).mock.calls.slice(-3) as unknown as Array<
    [string, { params?: Record<string, unknown>; skipErrorHandler?: boolean }]
  >;
  for (const [, options] of calls) {
    expect(options).toHaveProperty('skipErrorHandler', true);
    expect(options?.params).not.toHaveProperty('skipErrorHandler');
  }
});
