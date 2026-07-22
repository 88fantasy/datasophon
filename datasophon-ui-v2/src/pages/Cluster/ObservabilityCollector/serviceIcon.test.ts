import { describe, expect, it } from 'vitest';

import { serviceIconFor } from './serviceIcon';

function expectValidIcon(serviceName: string, keyword: string) {
  const icon = serviceIconFor(serviceName);
  expect(icon).toMatchObject({ keyword });
  expect(icon.src).toMatch(/^data:image\/(svg\+xml|png)/);
  expect(icon.width).toBeGreaterThan(0);
  expect(icon.height).toBeGreaterThan(0);
}

describe('serviceIconFor', () => {
  it('matches known infrastructure services by keyword, case-insensitively', () => {
    expectValidIcon('api-gateway', 'gateway');
    expectValidIcon('Redis-Cache', 'redis');
    expectValidIcon('mysql-primary', 'mysql');
    expectValidIcon('mongo-replica', 'mongo');
    expectValidIcon('kafka-broker', 'kafka');
    expectValidIcon('elasticsearch-node', 'elasticsearch');
    expectValidIcon('nacos-server', 'nacos');
    expectValidIcon('zookeeper-1', 'zookeeper');
  });

  it('matches big-data components from deploy/deployment-standalone.md', () => {
    expectValidIcon('hdfs-namenode-nn1', 'namenode');
    expectValidIcon('datanode-1', 'datanode');
    expectValidIcon('yarn-resourcemanager', 'resourcemanager');
    expectValidIcon('nodemanager-2', 'nodemanager');
    expectValidIcon('hive-metastore', 'hive');
    expectValidIcon('doris-fe', 'doris');
    expectValidIcon('valkey-master', 'valkey');
    expectValidIcon('juicefs-mount', 'juicefs');
    expectValidIcon('spark-client3', 'spark');
    expectValidIcon('flink-history', 'flink');
    expectValidIcon('kyuubi-server', 'kyuubi');
    expectValidIcon('dolphinscheduler-api', 'dolphinscheduler');
    expectValidIcon('nginx-proxy', 'nginx');
    expectValidIcon('otelcol-collector', 'otel');
    expectValidIcon('datart-server', 'datart');
    expectValidIcon('nexus-repo', 'nexus');
    expectValidIcon('rustfs-node', 'rustfs');
    expectValidIcon('chronyd', 'chrony');
  });

  it('gives apisix its own dedicated icon, distinct from the generic gateway fallback', () => {
    const apisix = serviceIconFor('apisix-gateway');
    const generic = serviceIconFor('my-custom-gateway');
    expect(apisix.keyword).toBe('apisix');
    expect(generic.keyword).toBe('gateway');
    expect(apisix.src).not.toBe(generic.src);
  });

  it('does not false-positive on unrelated names containing short substrings', () => {
    // "notification-service" 不应因含 "es" 而误判为 elasticsearch
    const icon = serviceIconFor('notification-service');
    expect(icon.keyword).toBe('');
  });

  it('falls back to a generic service icon for unmatched names', () => {
    const icon = serviceIconFor('unknown-custom-service');
    expect(icon.keyword).toBe('');
    expect(icon.src).toMatch(/^data:image\/svg\+xml/);
  });

  it('gives datasophon自有组件(api/worker/master) their own logo instead of the generic fallback', () => {
    expectValidIcon('datasophon-api', 'datasophon');
    expectValidIcon('datasophon-worker', 'datasophon');
    expectValidIcon('datasophon-master', 'datasophon');
  });
});
