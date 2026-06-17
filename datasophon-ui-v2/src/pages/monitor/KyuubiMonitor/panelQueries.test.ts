import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceKyuubiVars } from './panelQueries';

const CONN_TYPE_PLACEHOLDER = '$' + '{connType}';
const OP_TYPE_PLACEHOLDER = '$' + '{opType}';

describe('KyuubiMonitor panel queries', () => {
  it('defines every Kyuubi dashboard panel from KY01 through KY16', () => {
    const expectedIds = Array.from(
      { length: 16 },
      (_, index) => `KY${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces connType and opType placeholders inside metric names', () => {
    expect(
      replaceKyuubiVars(
        `kyuubi_${CONN_TYPE_PLACEHOLDER}_failed{instance=~"$instance"}`,
        {
          connType: 'connection_total_BATCH',
          opType: 'LaunchEngine',
          instance: 'kyuubi-1:10019',
          baseFilter: '',
          trendInterval: '5m',
        },
      ),
    ).toBe('kyuubi_connection_total_BATCH_failed{instance=~"kyuubi-1:10019"}');

    expect(
      replaceKyuubiVars(
        `kyuubi_operation_state_${OP_TYPE_PLACEHOLDER}_error_total{instance=~"$instance"}`,
        {
          connType: 'connection_total_INTERACTIVE',
          opType: 'ExecuteStatement',
          instance: 'kyuubi-2:10019',
          baseFilter: '',
          trendInterval: '5m',
        },
      ),
    ).toBe(
      'kyuubi_operation_state_ExecuteStatement_error_total{instance=~"kyuubi-2:10019"}',
    );
  });

  it('cleans an empty baseFilter prefix before instance filters', () => {
    expect(
      replaceKyuubiVars(
        'kyuubi_jvm_uptime{$baseFilter,instance=~"$instance"}',
        {
          instance: 'kyuubi-.+',
          baseFilter: '',
          connType: 'connection_total_INTERACTIVE',
          opType: 'ExecuteStatement',
          trendInterval: '5m',
        },
      ),
    ).toBe('kyuubi_jvm_uptime{instance=~"kyuubi-.+"}');
  });

  it('replaces trendInterval in increase windows', () => {
    expect(
      replaceKyuubiVars(
        'increase(kyuubi_connection_total_INTERACTIVE{instance=~"$instance"}[$trendInterval])',
        {
          instance: '.+',
          baseFilter: '',
          connType: 'connection_total_INTERACTIVE',
          opType: 'ExecuteStatement',
          trendInterval: '15m',
        },
      ),
    ).toBe(
      'increase(kyuubi_connection_total_INTERACTIVE{instance=~".+"}[15m])',
    );
  });

  it('keeps KY06, KY11, and KY12 as red error panels', () => {
    expect(PANEL_QUERIES.KY06).toMatchObject({
      type: 'instant',
      tone: 'error',
    });
    expect(JSON.stringify(PANEL_QUERIES.KY06)).toContain(
      `kyuubi_operation_state_${OP_TYPE_PLACEHOLDER}_error_total`,
    );

    expect(PANEL_QUERIES.KY11).toMatchObject({
      type: 'range',
      tone: 'error',
    });
    expect(JSON.stringify(PANEL_QUERIES.KY11)).toContain(
      `kyuubi_${CONN_TYPE_PLACEHOLDER}_failed`,
    );

    expect(PANEL_QUERIES.KY12).toMatchObject({
      type: 'multi-range',
      tone: 'error',
    });
    expect(JSON.stringify(PANEL_QUERIES.KY12)).toContain(
      `kyuubi_operation_state_${OP_TYPE_PLACEHOLDER}_error_total`,
    );
  });

  it('defines KY16 as JVM memory pool multi-range queries', () => {
    expect(PANEL_QUERIES.KY16).toMatchObject({
      type: 'multi-range',
      queries: [
        { label: 'Eden' },
        { label: 'Old Gen' },
        { label: 'Survivor' },
        { label: 'Metaspace' },
        { label: 'Code Cache' },
      ],
    });

    expect(JSON.stringify(PANEL_QUERIES.KY16)).toContain(
      'kyuubi_memory_usage_pools_PS_Eden_Space_used',
    );
  });
});
