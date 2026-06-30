const READONLY_OTEL_BUILTIN_RULE_NAMES = new Set([
  'Nexus实例只读',
  'Nexus线程死锁',
  'Nexus堆内存使用率',
  'Nexus文件描述符使用率',
  'DorisBE磁盘使用率',
  'DorisFE堆内存使用率',
  'Doris查询错误率',
]);

export function isReadonlyOtelBuiltinRule(
  record?: Pick<DATASOPHON.AlertQuotaResponse, 'alertQuotaName'> | null,
) {
  return !!record?.alertQuotaName && READONLY_OTEL_BUILTIN_RULE_NAMES.has(record.alertQuotaName);
}
