import type { PanelDef } from '../_shared/panelTypes';

export interface NginxDashboardVariables {
  instance: string;
}

export function replaceNginxVars(
  promql: string,
  variables: Partial<NginxDashboardVariables>,
): string {
  return promql.replace(/\$instance/g, variables.instance || '.+');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  // R1 — Status Stat
  N01: {
    type: 'instant',
    promql: 'nginx_up{instance=~"$instance"}',
  },
  N02: {
    type: 'instant',
    promql: 'sum(nginx_connections_active{instance=~"$instance"})',
  },
  N03: {
    type: 'instant',
    promql:
      'sum(irate(nginx_connections_accepted{instance=~"$instance"}[5m])) - sum(irate(nginx_connections_handled{instance=~"$instance"}[5m]))',
  },
  // R2 — Traffic
  N04: {
    type: 'range',
    promql:
      'sum(irate(nginx_http_requests_total{instance=~"$instance"}[5m]))',
  },
  N05: {
    type: 'multi-range',
    queries: [
      {
        label: 'Accepted',
        promql: 'irate(nginx_connections_accepted{instance=~"$instance"}[5m])',
      },
      {
        label: 'Handled',
        promql: 'irate(nginx_connections_handled{instance=~"$instance"}[5m])',
      },
    ],
  },
  // R3 — Saturation
  N06: {
    type: 'multi-range',
    queries: [
      {
        label: 'Active',
        promql: 'nginx_connections_active{instance=~"$instance"}',
      },
      {
        label: 'Reading',
        promql: 'nginx_connections_reading{instance=~"$instance"}',
      },
      {
        label: 'Writing',
        promql: 'nginx_connections_writing{instance=~"$instance"}',
      },
      {
        label: 'Waiting',
        promql: 'nginx_connections_waiting{instance=~"$instance"}',
      },
    ],
  },
};
