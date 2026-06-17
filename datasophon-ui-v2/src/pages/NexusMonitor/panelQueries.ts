import type { PanelDef } from '../PrometheusMonitor/panelQueries';

export interface NexusDashboardVariables {
  instance: string;
  job: string;
}

export function replaceNexusVars(
  promql: string,
  variables: Partial<NexusDashboardVariables>,
): string {
  return promql
    .replace(/\$instance/g, variables.instance || '.+')
    .replace(/\$job/g, variables.job || '.+');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  N01: {
    type: 'instant',
    promql: 'jvm_vm_uptime{instance=~"$instance",job=~"$job"}',
  },
  N02: {
    type: 'instant',
    promql: 'jvm_memory_heap_usage{instance=~"$instance",job=~"$job"} * 100',
  },
  N03: {
    type: 'instant',
    promql: 'jvm_fd_usage{instance=~"$instance",job=~"$job"} * 100',
  },
  N04: {
    type: 'instant',
    promql: 'readonly_enabled{instance=~"$instance",job=~"$job"}',
  },
  N05: {
    type: 'instant',
    promql: 'jvm_thread_states_count{instance=~"$instance",job=~"$job"}',
  },
  N06: {
    type: 'instant',
    promql:
      'jvm_thread_states_deadlock_count{instance=~"$instance",job=~"$job"}',
  },
  N07: {
    type: 'multi-range',
    queries: ['1xx', '2xx', '3xx', '4xx', '5xx'].map((code) => ({
      label: code,
      promql: `rate(org_eclipse_jetty_webapp_WebAppContext_${code}_responses_total{instance=~"$instance",job=~"$job"}[1m])`,
    })),
  },
  N08: {
    type: 'range',
    promql:
      'topk(10, sum by (__name__) (rate({__name__=~".*_exceptions_total",instance=~"$instance",job=~"$job"}[5m])) > 0)',
    seriesKey: '__name__',
  },
  N09: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        promql:
          'org_eclipse_jetty_webapp_WebAppContext_requests{quantile="0.5",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'p99',
        promql:
          'org_eclipse_jetty_webapp_WebAppContext_requests{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
    ],
  },
  N10: {
    type: 'multi-range',
    queries: [
      {
        label: 'Repository',
        promql:
          'org_sonatype_nexus_coreui_RepositoryComponent_read_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'Search',
        promql:
          'org_sonatype_nexus_coreui_SearchComponent_read_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'Browse',
        promql:
          'org_sonatype_nexus_coreui_BrowseComponent_read_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'Security',
        promql:
          'org_sonatype_nexus_rapture_internal_security_SecurityComponent_getPermissions_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
    ],
  },
  N11: {
    type: 'multi-range',
    queries: [
      {
        label: 'get',
        promql:
          'org_sonatype_nexus_blobstore_file_FileBlobStore_get_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'create',
        promql:
          'org_sonatype_nexus_blobstore_file_FileBlobStore_create_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'delete',
        promql:
          'org_sonatype_nexus_blobstore_file_FileBlobStore_delete_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
      {
        label: 'copy',
        promql:
          'org_sonatype_nexus_blobstore_file_FileBlobStore_copy_timer{quantile="0.99",instance=~"$instance",job=~"$job"} * 1000',
      },
    ],
  },
  N12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Max',
        promql: 'jvm_memory_heap_max{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Used',
        promql: 'jvm_memory_heap_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Committed',
        promql: 'jvm_memory_heap_committed{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  N13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Eden',
        promql:
          'jvm_memory_pools_PS_Eden_Space_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Old Gen',
        promql:
          'jvm_memory_pools_PS_Old_Gen_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Survivor',
        promql:
          'jvm_memory_pools_PS_Survivor_Space_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Metaspace',
        promql:
          'jvm_memory_pools_Metaspace_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Code Cache',
        promql:
          'jvm_memory_pools_Code_Cache_used{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  N14: {
    type: 'multi-range',
    queries: [
      {
        label: 'MarkSweep',
        promql:
          'rate(jvm_garbage_collectors_PS_MarkSweep_count{instance=~"$instance",job=~"$job"}[1m])',
      },
      {
        label: 'Scavenge',
        promql:
          'rate(jvm_garbage_collectors_PS_Scavenge_count{instance=~"$instance",job=~"$job"}[1m])',
      },
    ],
  },
  N15: {
    type: 'multi-range',
    queries: [
      {
        label: 'MarkSweep',
        promql:
          'rate(jvm_garbage_collectors_PS_MarkSweep_time{instance=~"$instance",job=~"$job"}[5m]) / rate(jvm_garbage_collectors_PS_MarkSweep_count{instance=~"$instance",job=~"$job"}[5m])',
      },
      {
        label: 'Scavenge',
        promql:
          'rate(jvm_garbage_collectors_PS_Scavenge_time{instance=~"$instance",job=~"$job"}[5m]) / rate(jvm_garbage_collectors_PS_Scavenge_count{instance=~"$instance",job=~"$job"}[5m])',
      },
    ],
  },
  N16: {
    type: 'multi-range',
    queries: [
      {
        label: 'Runnable',
        promql:
          'jvm_thread_states_runnable_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Blocked',
        promql:
          'jvm_thread_states_blocked_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Waiting',
        promql:
          'jvm_thread_states_waiting_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Timed Waiting',
        promql:
          'jvm_thread_states_timed_waiting_count{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  N17: {
    type: 'multi-range',
    queries: [
      {
        label: 'Queued Jobs',
        promql:
          '{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_jobs",instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Pool Size',
        promql:
          '{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_size",instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  N18: {
    type: 'multi-range',
    queries: [
      {
        label: 'Non-Heap',
        promql: 'jvm_memory_non_heap_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Direct Buffers',
        promql: 'jvm_buffers_direct_used{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'Mapped Buffers',
        promql: 'jvm_buffers_mapped_used{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
};

export const TIME_RANGE_SECONDS: Record<string, number> = {
  '5m': 300,
  '15m': 900,
  '1h': 3600,
  '6h': 21600,
  '24h': 86400,
  '7d': 604800,
};
