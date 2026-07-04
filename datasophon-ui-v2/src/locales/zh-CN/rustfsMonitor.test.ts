import { describe, expect, it } from 'vitest';
import messages from './rustfsMonitor';

describe('zh-CN RustFS monitor locale', () => {
  it('contains the same required keys as the RustFS page uses', () => {
    const localeMessages: Record<string, string> = messages;
    const requiredKeys = [
      'pages.rustfsMonitor.title',
      'pages.rustfsMonitor.panel.uptime',
      'pages.rustfsMonitor.panel.buckets',
      'pages.rustfsMonitor.panel.objects',
      'pages.rustfsMonitor.panel.drivesOnline',
      'pages.rustfsMonitor.panel.drivesOffline',
      'pages.rustfsMonitor.panel.s3OperationsByApi',
      'pages.rustfsMonitor.panel.httpRequestsByStatus',
      'pages.rustfsMonitor.panel.httpTraffic',
      'pages.rustfsMonitor.panel.httpRequestDuration',
      'pages.rustfsMonitor.panel.httpFailures',
      'pages.rustfsMonitor.panel.driveIoErrors',
      'pages.rustfsMonitor.panel.capacityUsedPercent',
      'pages.rustfsMonitor.panel.processCpuPercent',
      'pages.rustfsMonitor.panel.processMemory',
      'pages.rustfsMonitor.panel.driveCapacityByDrive',
      'pages.rustfsMonitor.panel.driveIopsByDrive',
      'pages.rustfsMonitor.panel.fileDescriptors',
      'pages.rustfsMonitor.panel.replicationActiveWorkers',
    ];

    for (const key of requiredKeys) {
      expect(localeMessages[key]).toBeTypeOf('string');
      expect(localeMessages[key]).not.toHaveLength(0);
    }
  });
});
