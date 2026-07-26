/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { describe, expect, it } from 'vitest';
import { ALL_PANEL_IDS, CLUSTER_DASHBOARD_JOB, PANEL_QUERIES } from './panelQueries';

describe('Cluster Dashboard panel queries', () => {
  it('defines all 4 panels and fixes the Doris job to the host_metrics job', () => {
    expect(ALL_PANEL_IDS).toHaveLength(4);
    for (const id of ALL_PANEL_IDS) {
      expect(PANEL_QUERIES[id], `${id} should be defined`).toBeDefined();
    }
    expect(CLUSTER_DASHBOARD_JOB).toBe('^node$');
  });

  it('computes per-node CPU usage% as non-idle rate over total rate', () => {
    const cpu = PANEL_QUERIES['CO-CPU'];
    expect(cpu.type).toBe('multi-range');
    if (cpu.type !== 'multi-range') return;
    expect(cpu.queries[0]).toMatchObject({
      metric: 'system.cpu.time',
      table: 'sum',
      rate: '1m',
      filtersNe: { state: 'idle' },
      denominatorMetric: 'system.cpu.time',
      denominatorTable: 'sum',
      scale: 100,
    });
  });

  it('groups network throughput by direction so rx/tx stay distinguishable', () => {
    const net = PANEL_QUERIES['CO-NET'];
    expect(net.type).toBe('multi-range');
    if (net.type !== 'multi-range') return;
    expect(net.queries[0]).toMatchObject({
      metric: 'system.network.io',
      table: 'sum',
      rate: '1m',
      groupBy: ['direction'],
    });
  });

  it('aggregates memory usage% cluster-wide via SUM(used)/SUM(total)', () => {
    expect(PANEL_QUERIES['CO-MEM-PCT']).toMatchObject({
      type: 'instant',
      metric: 'system.memory.usage',
      table: 'sum',
      agg: 'sum',
      filters: { state: 'used' },
      denominatorMetric: 'system.memory.usage',
      scale: 100,
    });
  });

  it('aggregates disk usage% for ext4/xfs filesystems, excluding pod mounts', () => {
    expect(PANEL_QUERIES['CO-DISK-PCT']).toMatchObject({
      type: 'instant',
      metric: 'system.filesystem.usage',
      table: 'sum',
      agg: 'sum',
      filters: { type: 'ext.*|xfs', state: 'used' },
      filtersNe: { mountpoint: '.*pod.*' },
      denominatorFilters: { type: 'ext.*|xfs' },
      denominatorFiltersNe: { mountpoint: '.*pod.*' },
    });
  });
});
