import { describe, expect, it } from 'vitest';
import { instantValues, zkSeriesData } from './zkMockData';

describe('ZooKeeperMonitor mock data', () => {
  it('contains the expected instant panel values', () => {
    expect(instantValues).toMatchObject({
      quorumSize: 3,
      leaderUptime: 7_254_000,
      jvmThreads: 85,
      deadlockedThreads: 0,
      aliveConnections: 42,
      openFileDescriptors: 356,
    });
  });

  it('covers every remaining range panel (Z16/Z19/Z20/Z23 移除,见 panelQueries.test.ts)', () => {
    const expectedIds = [
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

    expect(Object.keys(zkSeriesData).sort()).toEqual(expectedIds);
    for (const id of expectedIds) {
      expect(zkSeriesData[id]).not.toHaveLength(0);
    }
  });

  it('includes the required latency, error, and JVM memory series', () => {
    expect(new Set(zkSeriesData.Z08.map((point) => point.series))).toEqual(
      new Set(['max', 'avg', 'min']),
    );

    expect(new Set(zkSeriesData.Z15.map((point) => point.series))).toEqual(
      new Set([
        'conn_rejected',
        'conn_drop',
        'unrecoverable',
        'digest_mismatch',
      ]),
    );

    expect(new Set(zkSeriesData.Z21.map((point) => point.series))).toEqual(
      new Set(['G1 Old Gen', 'Metaspace', 'G1 Eden Space']),
    );
  });
});
