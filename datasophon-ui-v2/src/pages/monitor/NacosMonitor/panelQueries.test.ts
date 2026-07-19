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
import { ALL_PANEL_IDS, NACOS_JOB_FILTER, PANEL_QUERIES } from './panelQueries';

describe('NacosMonitor panel queries', () => {
  it('defines all Doris-backed panels and fixes the Doris job to NacosServer', () => {
    expect(ALL_PANEL_IDS).toHaveLength(18);
    for (const id of ALL_PANEL_IDS.filter((panelId) => panelId !== 'N06')) {
      expect(PANEL_QUERIES[id], `${id} should be defined`).toBeDefined();
    }
    expect(PANEL_QUERIES.N06).toBeUndefined();
    expect(NACOS_JOB_FILTER).toBe('^NacosServer$');
  });

  it('uses Nacos 3.x serviceCount and module-scoped filters', () => {
    expect(PANEL_QUERIES.N02).toMatchObject({
      type: 'instant',
      metric: 'nacos_monitor',
      filters: { module: 'naming', name: 'serviceCount' },
    });
    expect(JSON.stringify(PANEL_QUERIES)).not.toContain('domCount');
    expect(JSON.stringify(PANEL_QUERIES)).not.toContain('nacos_timer_seconds');
  });

  it('calculates HTTP QPS, latency and 5xx rate from summary count/sum fields', () => {
    const qps = PANEL_QUERIES.N07;
    const latency = PANEL_QUERIES.N08;
    const errors = PANEL_QUERIES.N09;
    expect(qps.type).toBe('multi-range');
    expect(latency.type).toBe('multi-range');
    expect(errors.type).toBe('multi-range');
    if (
      qps.type !== 'multi-range' ||
      latency.type !== 'multi-range' ||
      errors.type !== 'multi-range'
    ) {
      return;
    }
    expect(qps.queries[0]).toMatchObject({
      metric: 'http_server_requests_seconds',
      table: 'summary',
      field: 'count',
      rate: '1m',
    });
    expect(latency.queries[0]).toMatchObject({
      table: 'summary',
      field: 'sum',
      denominatorTable: 'summary',
      denominatorField: 'count',
      scale: 1000,
    });
    expect(errors.queries[0]).toMatchObject({
      filtersRegex: { status: '5..' },
      denominatorField: 'count',
      scale: 100,
    });
  });

  it('includes gRPC executor, heap ratio and GC pause descriptors', () => {
    expect(PANEL_QUERIES.N10).toMatchObject({ type: 'multi-range' });
    expect(PANEL_QUERIES.N11).toMatchObject({ type: 'multi-range' });
    expect(PANEL_QUERIES.N16).toMatchObject({ type: 'multi-range' });
    expect(PANEL_QUERIES.N18).toMatchObject({ type: 'multi-range' });

    const serialized = JSON.stringify({
      grpcLatency: PANEL_QUERIES.N10,
      grpcExecutor: PANEL_QUERIES.N11,
      heap: PANEL_QUERIES.N16,
      gc: PANEL_QUERIES.N18,
    });
    expect(serialized).toContain('grpc_server_requests_seconds_max');
    expect(serialized).toContain('grpc_server_executor');
    expect(serialized).toContain('jvm_memory_max_bytes');
    expect(serialized).toContain('jvm_gc_pause_seconds_max');
  });
});
