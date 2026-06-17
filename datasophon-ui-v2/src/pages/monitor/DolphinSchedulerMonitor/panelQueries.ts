import type { PanelDef } from '../_shared/panelTypes';

export interface DSDashboardVariables {
  application: string;
  instance: string;
}

export function replaceDSVars(
  promql: string,
  variables: Partial<DSDashboardVariables>,
): string {
  return promql
    .replace(/\$application/g, variables.application || 'master-server')
    .replace(/\$instance/g, variables.instance || '.+');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  'D-A01': {
    type: 'range',
    promql: 'process_cpu_usage{application="worker-server"}',
  },
  'D-A02': {
    type: 'range',
    promql: 'increase(ds_worker_full_submit_queue_count_total[1m])',
  },
  'D-A03': {
    type: 'range',
    promql: 'increase(ds_worker_overload_count_total[1m])',
  },
  'D-A04': {
    type: 'range',
    promql: 'ds_worker_task{}',
  },
  'D-A05': {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        promql: 'sum(increase(ds_worker_resource_download_count_total[5m]))',
      },
      {
        label: 'success',
        promql:
          'increase(ds_worker_resource_download_count_total{status="success"}[5m])',
      },
      {
        label: 'fail',
        promql:
          'increase(ds_worker_resource_download_count_total{status="fail"}[5m])',
      },
    ],
  },
  'D-A06': {
    type: 'range',
    promql: 'increase(ds_worker_resource_download_duration_seconds[5m])',
  },

  'D-B01': {
    type: 'instant',
    promql: 'sum(ds_task_execution_count_total)',
  },
  'D-B02': {
    type: 'instant',
    promql:
      'sum(ds_task_execution_count_total{result="success"}) / sum(ds_task_execution_count_total) * 100',
  },
  'D-B03': {
    type: 'instant',
    promql: 'sum(ds_master_quartz_job_executed_total)',
  },
  'D-B04': {
    type: 'instant',
    promql:
      'sum(ds_master_quartz_job_executed_total{result="success"}) / sum(ds_master_quartz_job_executed_total) * 100',
  },
  'D-B05': {
    type: 'range',
    promql: 'increase(ds_master_overload_count_total[1m])',
  },
  'D-B06': {
    type: 'range',
    promql: 'increase(ds_master_consume_command_count_total[1m])',
  },
  'D-B07': {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        promql: 'sum(increase(ds_master_quartz_job_executed_total[1m]))',
      },
      {
        label: 'success',
        promql:
          'increase(ds_master_quartz_job_executed_total{result="success"}[1m])',
      },
      {
        label: 'failure',
        promql:
          'increase(ds_master_quartz_job_executed_total{result="failure"}[1m])',
      },
    ],
  },
  'D-B08': {
    type: 'multi-range',
    queries: [
      {
        label: 'avg',
        promql:
          'rate(ds_master_quartz_job_execution_time_seconds_sum[1m]) / rate(ds_master_quartz_job_execution_time_seconds_count[1m])',
      },
      {
        label: 'max',
        promql: 'ds_master_quartz_job_execution_time_seconds_max',
      },
    ],
  },
  'D-B09': {
    type: 'multi-range',
    queries: [
      {
        label: 'total',
        promql: 'sum(increase(ds_task_execution_count_total[1m]))',
      },
      {
        label: 'success',
        promql: 'increase(ds_task_execution_count_total{result="success"}[1m])',
      },
    ],
  },
  'D-B10': {
    type: 'multi-range',
    queries: [
      {
        label: 'avg',
        promql:
          'rate(ds_task_execution_duration_seconds_sum[1m]) / rate(ds_task_execution_duration_seconds_count[1m]) * 1000',
      },
      {
        label: 'max',
        promql: 'ds_task_execution_duration_seconds_max * 1000',
      },
    ],
  },
  'D-B11': {
    type: 'multi-range',
    queries: [
      {
        label: 'submit',
        promql:
          'sum(increase(ds_workflow_instance_count_total{state="submit"}[1m]))',
      },
      {
        label: 'success',
        promql:
          'sum(increase(ds_workflow_instance_count_total{state="success"}[1m]))',
      },
      {
        label: 'fail',
        promql:
          'sum(increase(ds_workflow_instance_count_total{state="fail"}[1m]))',
      },
      {
        label: 'timeout',
        promql:
          'sum(increase(ds_workflow_instance_count_total{state="timeout"}[1m]))',
      },
    ],
  },
  'D-B12': {
    type: 'multi-range',
    queries: [
      {
        label: 'dispatch',
        promql: 'sum(increase(ds_task_dispatch_count_total[1m]))',
      },
      {
        label: 'failure',
        promql: 'sum(increase(ds_task_dispatch_failure_count_total[1m]))',
      },
      {
        label: 'error',
        promql: 'sum(increase(ds_task_dispatch_error_count_total[1m]))',
      },
    ],
  },
  'D-B13': {
    type: 'multi-range',
    queries: [
      {
        label: 'submit',
        promql:
          'sum(increase(ds_task_instance_count_total{state="submit"}[1m]))',
      },
      {
        label: 'success',
        promql:
          'sum(increase(ds_task_instance_count_total{state="success"}[1m]))',
      },
      {
        label: 'fail',
        promql: 'sum(increase(ds_task_instance_count_total{state="fail"}[1m]))',
      },
      {
        label: 'retry',
        promql:
          'sum(increase(ds_task_instance_count_total{state="retry"}[1m]))',
      },
    ],
  },

  'D-C01': {
    type: 'instant',
    promql:
      'process_uptime_seconds{application="$application", instance=~"$instance"}',
  },
  'D-C02': {
    type: 'instant',
    promql:
      'sum(jvm_memory_used_bytes{application="$application", instance=~"$instance", area="heap"}) * 100 / sum(jvm_memory_max_bytes{application="$application", instance=~"$instance", area="heap"})',
  },
  'D-C03': {
    type: 'instant',
    promql:
      'sum(jvm_memory_used_bytes{application="$application", instance=~"$instance", area="nonheap"}) * 100 / sum(jvm_memory_max_bytes{application="$application", instance=~"$instance", area="nonheap"})',
  },
  'D-C04': {
    type: 'range',
    promql:
      'sum(rate(http_server_requests_seconds_count{application="$application", instance=~"$instance"}[1m]))',
  },
  'D-C05': {
    type: 'range',
    promql:
      'sum(rate(http_server_requests_seconds_count{application="$application", instance=~"$instance", status=~"5.."}[1m]))',
  },
  'D-C06': {
    type: 'multi-range',
    queries: [
      {
        label: 'avg',
        promql:
          'sum(rate(http_server_requests_seconds_sum{application="$application", instance=~"$instance", status!~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count{application="$application", instance=~"$instance", status!~"5.."}[1m]))',
      },
      {
        label: 'max',
        promql:
          'max(http_server_requests_seconds_max{application="$application", instance=~"$instance", status!~"5.."})',
      },
    ],
  },
  'D-C07': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        promql:
          'sum(jvm_memory_used_bytes{application="$application", instance=~"$instance", area="heap"})',
      },
      {
        label: 'committed',
        promql:
          'sum(jvm_memory_committed_bytes{application="$application", instance=~"$instance", area="heap"})',
      },
      {
        label: 'max',
        promql:
          'sum(jvm_memory_max_bytes{application="$application", instance=~"$instance", area="heap"})',
      },
    ],
  },
  'D-C08': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        promql:
          'sum(jvm_memory_used_bytes{application="$application", instance=~"$instance", area="nonheap"})',
      },
      {
        label: 'committed',
        promql:
          'sum(jvm_memory_committed_bytes{application="$application", instance=~"$instance", area="nonheap"})',
      },
      {
        label: 'max',
        promql:
          'sum(jvm_memory_max_bytes{application="$application", instance=~"$instance", area="nonheap"})',
      },
    ],
  },
  'D-C09': {
    type: 'multi-range',
    queries: [
      {
        label: 'system',
        promql:
          'system_cpu_usage{application="$application", instance=~"$instance"}',
      },
      {
        label: 'process',
        promql:
          'process_cpu_usage{application="$application", instance=~"$instance"}',
      },
    ],
  },
  'D-C10': {
    type: 'multi-range',
    queries: [
      {
        label: 'load_1m',
        promql:
          'system_load_average_1m{application="$application", instance=~"$instance"}',
      },
      {
        label: 'cpu_cores',
        promql:
          'system_cpu_count{application="$application", instance=~"$instance"}',
      },
    ],
  },
  'D-C11': {
    type: 'multi-range',
    queries: [
      {
        label: 'live',
        promql:
          'jvm_threads_live_threads{application="$application", instance=~"$instance"}',
      },
      {
        label: 'daemon',
        promql:
          'jvm_threads_daemon_threads{application="$application", instance=~"$instance"}',
      },
      {
        label: 'peak',
        promql:
          'jvm_threads_peak_threads{application="$application", instance=~"$instance"}',
      },
      {
        label: 'tomcat_busy',
        promql:
          'tomcat_threads_busy_threads{application="$application", instance=~"$instance"}',
      },
      {
        label: 'tomcat_current',
        promql:
          'tomcat_threads_current_threads{application="$application", instance=~"$instance"}',
      },
    ],
  },
  'D-C12': {
    type: 'range',
    promql:
      'increase(logback_events_total{application="$application", instance=~"$instance"}[1m])',
    seriesKey: 'level',
  },
  'D-C13': {
    type: 'range',
    promql:
      'rate(jvm_gc_pause_seconds_count{application="$application", instance=~"$instance"}[1m])',
    seriesKey: 'cause',
  },
};
