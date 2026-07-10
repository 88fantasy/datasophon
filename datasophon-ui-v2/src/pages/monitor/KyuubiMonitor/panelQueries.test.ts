import { describe, expect, it } from 'vitest';
import {
  buildKyuubiPanelQueries,
  KYUUBI_CONN_TYPES,
  KYUUBI_OP_TYPES,
  PANEL_QUERIES,
} from './panelQueries';

describe('KyuubiMonitor panel queries', () => {
  it('defines every Kyuubi dashboard panel from KY01 through KY16', () => {
    const expectedIds = Array.from(
      { length: 16 },
      (_, index) => `KY${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('uses the real Doris table for Kyuubi gauge and sum metrics', () => {
    expect(PANEL_QUERIES.KY01).toMatchObject({
      type: 'instant',
      metric: 'kyuubi_jvm_uptime',
      agg: 'count',
    });
    expect(PANEL_QUERIES.KY06).toMatchObject({
      type: 'range-stat',
      table: 'sum',
      rate: '5m',
      scale: 300,
    });

    const ky13 = PANEL_QUERIES.KY13;
    expect(ky13.type).toBe('multi-range');
    if (ky13.type === 'multi-range') {
      expect(ky13.queries[0]).toMatchObject({
        metric: 'kyuubi_backend_service_fetch_result_rows_rate_total',
        table: 'sum',
        rate: '5m',
        scale: 300,
      });
    }
  });

  it('substitutes selected connection and operation types into exact metric names', () => {
    const panels = buildKyuubiPanelQueries(
      'thrift_http_connection',
      'LaunchEngine',
      '1h',
    );

    expect(panels.KY06).toMatchObject({
      metric: 'kyuubi_operation_state_LaunchEngine_error_total',
      rate: '1h',
      scale: 3600,
    });

    const ky11 = panels.KY11;
    const ky12 = panels.KY12;
    expect(ky11.type).toBe('multi-range');
    expect(ky12.type).toBe('multi-range');
    if (ky11.type === 'multi-range' && ky12.type === 'multi-range') {
      expect(ky11.queries[0].metric).toBe(
        'kyuubi_thrift_http_connection_failed',
      );
      expect(ky12.queries).toEqual([
        {
          label: 'Operation Error',
          metric: 'kyuubi_operation_state_LaunchEngine_error_total',
          table: 'sum',
        },
      ]);
    }
  });

  it('uses the observed JVM memory pool metric names', () => {
    const ky16 = PANEL_QUERIES.KY16;
    expect(ky16.type).toBe('multi-range');
    if (ky16.type !== 'multi-range') return;

    expect(ky16.queries.map((query) => query.metric)).toEqual([
      'kyuubi_memory_usage_pools_Eden_Space_used',
      'kyuubi_memory_usage_pools_Tenured_Gen_used',
      'kyuubi_memory_usage_pools_Survivor_Space_used',
      'kyuubi_memory_usage_pools_Metaspace_used',
      'kyuubi_memory_usage_pools_Code_Cache_used',
    ]);
  });

  it('only offers Kyuubi connection and operation types supported by the official dashboard', () => {
    expect(KYUUBI_CONN_TYPES).toEqual([
      'thrift_binary_connection',
      'rest_connection',
      'thrift_http_connection',
      'metadata_request',
    ]);
    expect(KYUUBI_OP_TYPES).toEqual([
      'ExecuteStatement',
      'BatchJobSubmission',
      'LaunchEngine',
    ]);
  });
});
