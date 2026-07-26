import { describe, expect, it } from 'vitest';
import messages from './clusterDashboard';

const REQUIRED_KEYS = [
  'pages.clusterDashboard.title',
  'pages.clusterDashboard.stat.hostTotal',
  'pages.clusterDashboard.stat.serviceTotal',
  'pages.clusterDashboard.stat.alertTotal',
  'pages.clusterDashboard.stat.criticalAlertTotal',
  'pages.clusterDashboard.stat.changeLabel',
  'pages.clusterDashboard.panel.cpu',
  'pages.clusterDashboard.panel.network',
  'pages.clusterDashboard.panel.resourceUsage',
  'pages.clusterDashboard.panel.alertTrend',
  'pages.clusterDashboard.panel.recentAlerts',
  'pages.clusterDashboard.panel.serviceHealth',
  'pages.clusterDashboard.panel.profile',
  'pages.clusterDashboard.resource.cpu',
  'pages.clusterDashboard.resource.memory',
  'pages.clusterDashboard.resource.disk',
  'pages.clusterDashboard.alertLevel.warning',
  'pages.clusterDashboard.alertLevel.exception',
  'pages.clusterDashboard.column.level',
  'pages.clusterDashboard.column.target',
  'pages.clusterDashboard.column.hostname',
  'pages.clusterDashboard.column.createTime',
  'pages.clusterDashboard.column.service',
  'pages.clusterDashboard.column.roles',
  'pages.clusterDashboard.column.health',
  'pages.clusterDashboard.column.alertNum',
  'pages.clusterDashboard.column.state',
  'pages.clusterDashboard.profile.frame',
  'pages.clusterDashboard.profile.cpuArchitecture',
  'pages.clusterDashboard.profile.nodeCount',
  'pages.clusterDashboard.profile.totalCores',
  'pages.clusterDashboard.profile.totalMem',
  'pages.clusterDashboard.profile.totalDisk',
  'pages.clusterDashboard.profile.clusterState',
  'pages.clusterDashboard.profile.createTime',
  'pages.clusterDashboard.emptyText.alerts',
  'pages.clusterDashboard.emptyText.services',
  'pages.clusterDashboard.viewAll',
  'pages.clusterDashboard.viewMore',
];

describe('en-US cluster dashboard locale', () => {
  it('contains all keys used by the cluster overview dashboard', () => {
    for (const key of REQUIRED_KEYS) {
      expect(messages[key as keyof typeof messages]).toBeTypeOf('string');
      expect(messages[key as keyof typeof messages]).not.toHaveLength(0);
    }
  });
});
