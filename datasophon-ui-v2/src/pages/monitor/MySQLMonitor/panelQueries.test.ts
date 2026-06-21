import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceMySQLVars } from './panelQueries';

describe('MySQLMonitor panel queries', () => {
  it('defines every MySQL dashboard panel from M01 through M17', () => {
    const expectedIds = Array.from(
      { length: 17 },
      (_, index) => `M${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces MySQL variables and rate intervals with defaults', () => {
    expect(
      replaceMySQLVars(
        'rate(mysql_global_status_queries{job=~"$job", instance=~"$instance"}[$__interval])',
        { instance: 'mysql-exporter-1:9104', job: 'mysql' },
        '2m',
      ),
    ).toBe(
      'rate(mysql_global_status_queries{job=~"mysql", instance=~"mysql-exporter-1:9104"}[2m])',
    );

    expect(
      replaceMySQLVars(
        'mysql_up{job=~"$job", instance=~"$instance"}',
        {},
        '1m',
      ),
    ).toBe('mysql_up{job=~".+", instance=~".+"}');
  });

  it('normalizes M16 handlers to instance and never host', () => {
    const def = PANEL_QUERIES.M16;

    expect(JSON.stringify(def)).not.toContain('$host');
    expect(def).toMatchObject({
      type: 'range',
      promql: expect.stringContaining('instance=~"$instance"'),
    });
  });

  it('keeps the M14 internal memory multi-range queries', () => {
    expect(PANEL_QUERIES.M14).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          label: 'Buffer Pool Data',
          promql: expect.stringContaining(
            'mysql_global_status_buffer_pool_pages',
          ),
        },
        {
          label: 'Log Buffer',
          promql: expect.stringContaining(
            'mysql_global_variables_innodb_log_buffer_size',
          ),
        },
        {
          label: 'Key Buffer',
          promql: expect.stringContaining(
            'mysql_global_variables_key_buffer_size',
          ),
        },
        {
          label: 'Adaptive Hash',
          promql: expect.stringContaining(
            'mysql_global_status_innodb_mem_adaptive_hash',
          ),
        },
      ],
    });
  });
});
