import type { TopologyNode, ZoneId } from './types';

/** 全景展示服务器 / Node；K8s 分区展示集群级 Service / CR，不归属到单个 Node。 */
export function visibleTopologyNodes(
  nodes: TopologyNode[],
  zone: ZoneId | null,
  hostId: string | null,
): TopologyNode[] {
  if (zone === 'k8s')
    return nodes.filter(
      (node) => node.zone === 'k8s' && node.kind === 'service',
    );
  if (hostId)
    return nodes.filter(
      (node) => node.parentId === hostId && node.zone === zone,
    );
  return nodes.filter(
    (node) =>
      !node.parentId &&
      (!zone || node.zone === zone) &&
      !(node.zone === 'k8s' && node.kind === 'service'),
  );
}
