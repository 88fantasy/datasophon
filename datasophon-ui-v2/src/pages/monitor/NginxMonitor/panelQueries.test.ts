import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceNginxVars } from './panelQueries';

describe('NginxMonitor panel queries', () => {
  it('defines all 6 Nginx dashboard panels N01 through N06', () => {
    const expectedIds = ['N01', 'N02', 'N03', 'N04', 'N05', 'N06'];
    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces $instance variable correctly', () => {
    expect(
      replaceNginxVars('nginx_up{instance=~"$instance"}', {
        instance: '10.0.0.1:9113',
      }),
    ).toBe('nginx_up{instance=~"10.0.0.1:9113"}');

    expect(
      replaceNginxVars('nginx_up{instance=~"$instance"}', {}),
    ).toBe('nginx_up{instance=~".+"}');
  });

  it('N01 is instant query for nginx_up', () => {
    expect(PANEL_QUERIES.N01).toMatchObject({
      type: 'instant',
      promql: 'nginx_up{instance=~"$instance"}',
    });
  });

  it('N03 Dropped Connections is instant derived from accepted minus handled', () => {
    const n03 = PANEL_QUERIES.N03;
    expect(n03.type).toBe('instant');
    if (n03.type === 'instant') {
      expect(n03.promql).toContain('nginx_connections_accepted');
      expect(n03.promql).toContain('nginx_connections_handled');
    }
  });

  it('N05 Processed Connections is multi-range with Accepted and Handled', () => {
    expect(PANEL_QUERIES.N05).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'Accepted' }, { label: 'Handled' }],
    });
  });

  it('N06 Active Connections is multi-range with 4 series', () => {
    expect(PANEL_QUERIES.N06).toMatchObject({
      type: 'multi-range',
      queries: [
        { label: 'Active' },
        { label: 'Reading' },
        { label: 'Writing' },
        { label: 'Waiting' },
      ],
    });
  });
});
