import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES } from './panelQueries';

describe('ZooKeeperMonitor panel queries', () => {
  it('defines every remaining ZooKeeper dashboard panel (election_time/fsynctime/snapshottime/jvm_pause_time_ms 因采集侧整体丢弃已移除 Z16/Z19/Z20/Z23,详见 zookeeper-otel-verification.md)', () => {
    const expectedIds = [
      'Z01',
      'Z02',
      'Z03',
      'Z04',
      'Z05',
      'Z06',
      'Z07',
      'Z08',
      'Z09',
      'Z10',
      'Z11',
      'Z12',
      'Z13',
      'Z14',
      'Z15',
      'Z17',
      'Z18',
      'Z21',
      'Z22',
    ];

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('Z01-Z06 are instant Doris descriptors', () => {
    expect(PANEL_QUERIES.Z01).toMatchObject({
      type: 'instant',
      metric: 'quorum_size',
      agg: 'max',
    });
    expect(PANEL_QUERIES.Z02).toMatchObject({
      type: 'instant',
      metric: 'leader_uptime',
      agg: 'max',
    });
    expect(PANEL_QUERIES.Z03).toMatchObject({
      type: 'instant',
      metric: 'jvm_threads_current',
      agg: 'max',
    });
    expect(PANEL_QUERIES.Z04).toMatchObject({
      type: 'instant',
      metric: 'jvm_threads_deadlocked',
      agg: 'max',
    });
    expect(PANEL_QUERIES.Z05).toMatchObject({
      type: 'instant',
      metric: 'num_alive_connections',
      agg: 'sum',
    });
    expect(PANEL_QUERIES.Z06).toMatchObject({
      type: 'instant',
      metric: 'open_file_descriptor_count',
      agg: 'max',
    });
  });

  it('Z15/Z18 use sum table without rate for cumulative counters', () => {
    for (const id of ['Z15', 'Z18']) {
      const panel = PANEL_QUERIES[id];
      expect(panel.type).toBe('multi-range');
      if (panel.type !== 'multi-range') continue;
      for (const query of panel.queries) {
        expect(query.table).toBe('sum');
        expect(query.rate).toBeUndefined();
      }
    }
  });

  it('Z13 packets_received/sent 是 gauge 类型(真实沙箱验证:# TYPE packets_sent gauge,非 counter),不带 table/rate', () => {
    const panel = PANEL_QUERIES.Z13;
    expect(panel.type).toBe('multi-range');
    if (panel.type !== 'multi-range') return;
    for (const query of panel.queries) {
      expect(query.table).toBeUndefined();
      expect(query.rate).toBeUndefined();
    }
  });

  it('Z21/Z22 carry JVM attribute groupBy and summary count field', () => {
    const z21 = PANEL_QUERIES.Z21;
    expect(z21.type).toBe('multi-range');
    if (z21.type === 'multi-range') {
      expect(z21.queries[0]).toMatchObject({
        metric: 'jvm_memory_pool_bytes_used',
        groupBy: ['pool'],
      });
    }

    const z22 = PANEL_QUERIES.Z22;
    expect(z22.type).toBe('multi-range');
    if (z22.type === 'multi-range') {
      expect(z22.queries[0]).toMatchObject({
        metric: 'jvm_gc_collection_seconds',
        table: 'summary',
        field: 'count',
        rate: '5m',
        groupBy: ['gc'],
      });
    }
  });
});
