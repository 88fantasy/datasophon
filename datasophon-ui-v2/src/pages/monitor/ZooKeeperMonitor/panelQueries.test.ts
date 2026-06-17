import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceZKVars } from './panelQueries';

describe('ZooKeeperMonitor panel queries', () => {
  it('defines every ZooKeeper dashboard panel from Z01 through Z23', () => {
    const expectedIds = Array.from(
      { length: 23 },
      (_, index) => `Z${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces ZooKeeper variables without requiring an interval variable', () => {
    expect(
      replaceZKVars('avg_latency{instance=~"$instance",job=~"$job"}', {
        instance: 'zk-1:7000',
        job: 'zookeeper',
      }),
    ).toBe('avg_latency{instance=~"zk-1:7000",job=~"zookeeper"}');

    expect(
      replaceZKVars('watch_count{instance=~"$instance",job=~"$job"}', {}),
    ).toBe('watch_count{instance=~".+",job=~".+"}');
  });

  it('keeps the error and JVM panels required by the spec', () => {
    expect(PANEL_QUERIES.Z15).toMatchObject({
      type: 'multi-range',
      queries: [
        { label: 'conn_rejected' },
        { label: 'conn_drop' },
        { label: 'unrecoverable' },
        { label: 'digest_mismatch' },
      ],
    });

    expect(PANEL_QUERIES.Z21).toMatchObject({
      type: 'range',
      promql: 'jvm_memory_pool_bytes_used{instance=~"$instance",job=~"$job"}',
      seriesKey: 'pool',
    });
  });
});
