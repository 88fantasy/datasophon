import { describe, expect, it } from 'vitest';
import messages from './zookeeperMonitor';

describe('zh-CN ZooKeeper monitor locale', () => {
  it('contains the same required keys as the ZooKeeper page uses', () => {
    const localeMessages: Record<string, string> = messages;
    const requiredKeys = [
      'pages.zookeeperMonitor.title',
      'pages.zookeeperMonitor.toolbar.job',
      'pages.zookeeperMonitor.panel.quorumSize',
      'pages.zookeeperMonitor.panel.leaderUptime',
      'pages.zookeeperMonitor.panel.jvmThreads',
      'pages.zookeeperMonitor.panel.deadlockedThreads',
      'pages.zookeeperMonitor.panel.aliveConnections',
      'pages.zookeeperMonitor.panel.openFileDescriptors',
      'pages.zookeeperMonitor.panel.outstandingRequests',
      'pages.zookeeperMonitor.panel.requestLatency',
      'pages.zookeeperMonitor.panel.sessions',
      'pages.zookeeperMonitor.panel.znodes',
      'pages.zookeeperMonitor.panel.approximateDataSize',
      'pages.zookeeperMonitor.panel.watchCount',
      'pages.zookeeperMonitor.panel.packets',
      'pages.zookeeperMonitor.panel.aliveConnectionsTrend',
      'pages.zookeeperMonitor.panel.connectionDataErrors',
      'pages.zookeeperMonitor.panel.learnersObservers',
      'pages.zookeeperMonitor.panel.quorumCounts',
      'pages.zookeeperMonitor.panel.jvmMemoryPool',
      'pages.zookeeperMonitor.panel.gcCollectionRate',
    ];

    for (const key of requiredKeys) {
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key]).not.toHaveLength(0);
    }
  });
});
