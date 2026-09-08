import type { TopologyNode, ZoneId } from './types';

/** 全景在各分区内展示服务器（K8s 为 Service / CR），进入服务器后展示中间件。 */
export function visibleTopologyNodes(
  nodes: TopologyNode[],
  zone: ZoneId | null,
  hostId: string | null,
): TopologyNode[] {
  if (hostId)
    return nodes.filter(
      (node) => node.parentId === hostId && node.zone === zone,
    );
  return nodes.filter(
    (node) => !node.parentId && (!zone || node.zone === zone),
  );
}
