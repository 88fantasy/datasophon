import type { DorisPanelDescriptor } from '../_shared/dorisService';

export type DSApplication =
  | 'master-server'
  | 'worker-server'
  | 'api-server'
  | 'alert-server';

export interface DSDashboardVariables {
  application: string;
  instance: string;
}

const MASTER_PANEL_IDS: string[] = [
  'D-B01',
  'D-B02',
  'D-B03',
  'D-B04',
  'D-B05',
  'D-B06',
  'D-B07',
  'D-B08',
  'D-B09',
  'D-B10',
  'D-B11',
  'D-B12',
  'D-B13',
  'D-C01',
  'D-C02',
  'D-C03',
  'D-C04',
  'D-C05',
  'D-C06',
  'D-C07',
  'D-C08',
  'D-C09',
  'D-C10',
  'D-C11',
  'D-C12',
  'D-C13',
];

const WORKER_PANEL_IDS: string[] = [
  'D-A01',
  'D-A02',
  'D-A03',
  'D-A04',
  'D-A05',
  'D-A06',
  'D-C01',
  'D-C02',
  'D-C03',
  'D-C04',
  'D-C05',
  'D-C06',
  'D-C07',
  'D-C08',
  'D-C09',
  'D-C10',
  'D-C11',
  'D-C12',
  'D-C13',
];

const SPRING_PANEL_IDS: string[] = [
  'D-C01',
  'D-C02',
  'D-C03',
  'D-C04',
  'D-C05',
  'D-C06',
  'D-C07',
  'D-C08',
  'D-C09',
  'D-C10',
  'D-C11',
  'D-C12',
  'D-C13',
];

export function getDSSegmentPanelIds(segment: DSApplication): string[] {
  switch (segment) {
    case 'master-server':
      return MASTER_PANEL_IDS;
    case 'worker-server':
      return WORKER_PANEL_IDS;
    case 'api-server':
    case 'alert-server':
      return SPRING_PANEL_IDS;
  }
}

export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  'D-A01': {
    type: 'multi-range',
    queries: [{ label: 'process_cpu_usage', metric: 'process_cpu_usage' }],
  },
  'D-A02': {
    type: 'multi-range',
    queries: [
      {
        label: 'full_submit_queue',
        metric: 'ds_worker_full_submit_queue_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
    ],
  },
  'D-A03': {
    type: 'multi-range',
    queries: [
      {
        label: 'overload',
        metric: 'ds_worker_overload_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
    ],
  },
  'D-A04': {
    type: 'multi-range',
    queries: [{ label: 'worker_task', metric: 'ds_task_running' }],
  },
  'D-A05': {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        metric: 'ds_worker_resource_download_count_total',
        table: 'sum',
        rate: '5m',
        scale: 300,
      },
      {
        label: 'success',
        metric: 'ds_worker_resource_download_count_total',
        table: 'sum',
        rate: '5m',
        scale: 300,
        filters: { status: 'success' },
      },
      {
        label: 'fail',
        metric: 'ds_worker_resource_download_count_total',
        table: 'sum',
        rate: '5m',
        scale: 300,
        filters: { status: 'fail' },
      },
    ],
  },
  'D-A06': {
    type: 'multi-range',
    queries: [
      {
        label: 'duration',
        metric: 'ds_worker_resource_download_duration_seconds_max',
      },
    ],
  },

  'D-B01': {
    type: 'instant',
    metric: 'ds_task_instance_count_total',
    table: 'sum',
    agg: 'sum',
  },
  'D-B02': {
    type: 'instant',
    metric: 'ds_task_instance_count_total',
    table: 'sum',
    agg: 'sum',
    filters: { state: 'success' },
    denominatorMetric: 'ds_task_instance_count_total',
    denominatorTable: 'sum',
    scale: 100,
  },
  'D-B03': {
    type: 'instant',
    metric: 'ds_workflow_instance_count_total',
    table: 'sum',
    agg: 'sum',
  },
  'D-B04': {
    type: 'instant',
    metric: 'ds_workflow_instance_count_total',
    table: 'sum',
    agg: 'sum',
    filters: { state: 'success' },
    denominatorMetric: 'ds_workflow_instance_count_total',
    denominatorTable: 'sum',
    scale: 100,
  },
  'D-B05': {
    type: 'multi-range',
    queries: [
      {
        label: 'overload',
        metric: 'ds_master_scheduler_failover_check_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
    ],
  },
  'D-B06': {
    type: 'multi-range',
    queries: [
      {
        label: 'consume_command',
        metric: 'ds_task_dispatch_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
    ],
  },
  'D-B07': {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        metric: 'ds_task_dispatch_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
      {
        label: 'failure',
        metric: 'ds_task_dispatch_failure_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
      {
        label: 'error',
        metric: 'ds_task_dispatch_error_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
    ],
  },
  'D-B08': {
    type: 'multi-range',
    queries: [
      {
        label: 'avg',
        metric: 'ds_workflow_command_query_duration_seconds',
        table: 'summary',
        field: 'sum',
        rate: '1m',
        denominatorMetric: 'ds_workflow_command_query_duration_seconds',
        denominatorTable: 'summary',
        denominatorField: 'count',
      },
      {
        label: 'max',
        metric: 'ds_workflow_command_query_duration_seconds_max',
      },
    ],
  },
  'D-B09': {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        metric: 'ds_workflow_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
      {
        label: 'success',
        metric: 'ds_workflow_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'success' },
      },
    ],
  },
  'D-B10': {
    type: 'multi-range',
    queries: [
      {
        label: 'avg',
        metric: 'ds_workflow_instance_generate_duration_seconds',
        table: 'summary',
        field: 'sum',
        rate: '1m',
        denominatorMetric: 'ds_workflow_instance_generate_duration_seconds',
        denominatorTable: 'summary',
        denominatorField: 'count',
        scale: 1000,
      },
      {
        label: 'max',
        metric: 'ds_workflow_instance_generate_duration_seconds_max',
        scale: 1000,
      },
    ],
  },
  'D-B11': {
    type: 'multi-range',
    queries: [
      {
        label: 'submit',
        metric: 'ds_workflow_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'submit' },
      },
      {
        label: 'success',
        metric: 'ds_workflow_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'success' },
      },
      {
        label: 'fail',
        metric: 'ds_workflow_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'fail' },
      },
      {
        label: 'timeout',
        metric: 'ds_workflow_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'timeout' },
      },
    ],
  },
  'D-B12': {
    type: 'multi-range',
    queries: [
      {
        label: 'dispatch',
        metric: 'ds_task_dispatch_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
      {
        label: 'failure',
        metric: 'ds_task_dispatch_failure_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
      {
        label: 'error',
        metric: 'ds_task_dispatch_error_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
      },
    ],
  },
  'D-B13': {
    type: 'multi-range',
    queries: [
      {
        label: 'submit',
        metric: 'ds_task_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'submit' },
      },
      {
        label: 'success',
        metric: 'ds_task_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'success' },
      },
      {
        label: 'fail',
        metric: 'ds_task_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'fail' },
      },
      {
        label: 'retry',
        metric: 'ds_task_instance_count_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        filters: { state: 'retry' },
      },
    ],
  },

  'D-C01': {
    type: 'instant',
    metric: 'process_uptime_seconds',
    agg: 'max',
  },
  'D-C02': {
    type: 'instant',
    metric: 'jvm_memory_used_bytes',
    agg: 'sum',
    filters: { area: 'heap' },
    denominatorMetric: 'jvm_memory_max_bytes',
    denominatorFilters: { area: 'heap' },
    scale: 100,
  },
  'D-C03': {
    type: 'instant',
    metric: 'jvm_memory_used_bytes',
    agg: 'sum',
    filters: { area: 'nonheap' },
    denominatorMetric: 'jvm_memory_max_bytes',
    denominatorFilters: { area: 'nonheap' },
    scale: 100,
  },
  'D-C04': {
    type: 'multi-range',
    queries: [
      {
        label: 'requests',
        metric: 'http_server_requests_seconds',
        table: 'summary',
        field: 'count',
        rate: '1m',
      },
    ],
  },
  'D-C05': {
    type: 'multi-range',
    queries: [
      {
        label: '5xx',
        metric: 'http_server_requests_seconds',
        table: 'summary',
        field: 'count',
        rate: '1m',
        filtersRegex: { status: '5..' },
      },
    ],
  },
  'D-C06': {
    type: 'multi-range',
    queries: [
      {
        label: 'avg',
        metric: 'http_server_requests_seconds',
        table: 'summary',
        field: 'sum',
        rate: '1m',
        filtersNotRegex: { status: '5..' },
        denominatorMetric: 'http_server_requests_seconds',
        denominatorTable: 'summary',
        denominatorField: 'count',
        denominatorFiltersNotRegex: { status: '5..' },
      },
      {
        label: 'max',
        metric: 'http_server_requests_seconds_max',
        filtersNotRegex: { status: '5..' },
      },
    ],
  },
  'D-C07': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        metric: 'jvm_memory_used_bytes',
        filters: { area: 'heap' },
      },
      {
        label: 'committed',
        metric: 'jvm_memory_committed_bytes',
        filters: { area: 'heap' },
      },
      { label: 'max', metric: 'jvm_memory_max_bytes', filters: { area: 'heap' } },
    ],
  },
  'D-C08': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        metric: 'jvm_memory_used_bytes',
        filters: { area: 'nonheap' },
      },
      {
        label: 'committed',
        metric: 'jvm_memory_committed_bytes',
        filters: { area: 'nonheap' },
      },
      {
        label: 'max',
        metric: 'jvm_memory_max_bytes',
        filters: { area: 'nonheap' },
      },
    ],
  },
  'D-C09': {
    type: 'multi-range',
    queries: [
      { label: 'system', metric: 'system_cpu_usage' },
      { label: 'process', metric: 'process_cpu_usage' },
    ],
  },
  'D-C10': {
    type: 'multi-range',
    queries: [
      { label: 'load_1m', metric: 'system_load_average_1m' },
      { label: 'cpu_cores', metric: 'system_cpu_count' },
    ],
  },
  'D-C11': {
    type: 'multi-range',
    queries: [
      { label: 'live', metric: 'jvm_threads_live_threads' },
      { label: 'daemon', metric: 'jvm_threads_daemon_threads' },
      { label: 'peak', metric: 'jvm_threads_peak_threads' },
      { label: 'tomcat_busy', metric: 'tomcat_threads_busy_threads' },
      { label: 'tomcat_current', metric: 'tomcat_threads_current_threads' },
    ],
  },
  'D-C12': {
    type: 'multi-range',
    queries: [
      {
        label: 'level',
        metric: 'logback_events_total',
        table: 'sum',
        rate: '1m',
        scale: 60,
        groupBy: ['level'],
      },
    ],
  },
  'D-C13': {
    type: 'multi-range',
    queries: [
      {
        label: 'cause',
        metric: 'jvm_gc_pause_seconds',
        table: 'summary',
        field: 'count',
        rate: '1m',
        groupBy: ['cause'],
      },
    ],
  },
};
