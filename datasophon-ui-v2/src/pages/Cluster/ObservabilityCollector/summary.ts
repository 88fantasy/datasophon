import type { CollectorNodeMetrics } from './service';

export function sumFailedDropped(nodes: CollectorNodeMetrics[]) {
  return nodes.reduce((sum, node) => {
    if (!node.metrics) return sum;
    return (
      sum +
      node.metrics.sendFailedTotal +
      node.metrics.receiverFailedTotal +
      node.metrics.refusedTotal +
      node.metrics.processorDroppedTotal
    );
  }, 0);
}

export function queueUsage(nodes: CollectorNodeMetrics[]) {
  const totals = nodes.reduce(
    (acc, node) => {
      if (!node.metrics) return acc;
      return {
        size: acc.size + node.metrics.queueSize,
        capacity: acc.capacity + node.metrics.queueCapacity,
      };
    },
    { size: 0, capacity: 0 },
  );
  return totals.capacity > 0 ? (totals.size / totals.capacity) * 100 : 0;
}

export function maxProcessUptime(nodes: CollectorNodeMetrics[]) {
  return nodes.reduce(
    (max, node) => Math.max(max, node.metrics?.processUptime ?? 0),
    0,
  );
}
