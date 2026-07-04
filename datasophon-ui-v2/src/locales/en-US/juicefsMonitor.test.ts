import { describe, expect, it } from 'vitest';
import messages from './juicefsMonitor';

describe('en-US JuiceFS monitor locale', () => {
  it('contains the same required keys as the JuiceFS page uses', () => {
    const localeMessages: Record<string, string> = messages;
    const requiredKeys = [
      'pages.juicefsMonitor.title',
      'pages.juicefsMonitor.toolbar.volume',
      'pages.juicefsMonitor.panel.uptime',
      'pages.juicefsMonitor.panel.dataSize',
      'pages.juicefsMonitor.panel.files',
      'pages.juicefsMonitor.panel.clientSessions',
      'pages.juicefsMonitor.panel.blockCacheHitPercent',
      'pages.juicefsMonitor.panel.stagingBlocks',
      'pages.juicefsMonitor.panel.operations',
      'pages.juicefsMonitor.panel.ioThroughput',
      'pages.juicefsMonitor.panel.ioLatency',
      'pages.juicefsMonitor.panel.transactionLatency',
      'pages.juicefsMonitor.panel.objectsLatency',
      'pages.juicefsMonitor.panel.objectsRequests',
      'pages.juicefsMonitor.panel.objectErrorsAndTxRestarts',
      'pages.juicefsMonitor.panel.blockCacheSize',
      'pages.juicefsMonitor.panel.blockCacheHitRatio',
      'pages.juicefsMonitor.panel.objectsThroughput',
      'pages.juicefsMonitor.panel.clientCpuAndMemory',
    ];

    for (const key of requiredKeys) {
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key]).not.toHaveLength(0);
    }
  });
});
