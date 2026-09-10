import { TOPOLOGY_ZONES, type TopologyNode, type ZoneId } from './types';

/** Shared world coordinates: +x is right, +z is the front of the scene. */
export function layoutTopology(
  nodes: TopologyNode[],
  focusZone: ZoneId | null,
) {
  const zones = TOPOLOGY_ZONES.filter(
    (zone) =>
      (!focusZone || zone.id === focusZone) &&
      nodes.some((node) => node.zone === zone.id),
  ).map((zone) => {
    const members = nodes
      .filter((node) => node.zone === zone.id)
      .sort(
        (a, b) =>
          Number(b.kind === 'host') - Number(a.kind === 'host') ||
          a.id.localeCompare(b.id),
      );
    const columns = Math.max(
      1,
      Math.ceil(Math.sqrt(members.length * (zone.id === 'k8s' ? 1.4 : 0.85))),
    );
    const rows = Math.max(1, Math.ceil(members.length / columns));
    return {
      zone,
      members,
      columns,
      width: Math.max(zone.id === 'k8s' ? 13 : 7, columns * 3.3 + 1.7),
      depth: Math.max(8, rows * 4.2 + 3.6),
      x: 0,
      z: 0,
    };
  });

  if (!zones.length) return { nodes: [], zones: [], width: 12, depth: 8 };

  if (!focusZone) {
    let cursor = 0;
    for (const zone of zones.filter(
      (item) =>
        item.zone.id !== 'vm' || !zones.some(({ zone }) => zone.id === 'doris'),
    )) {
      zone.x = cursor + zone.width / 2;
      cursor += zone.width + 2.4;
    }
    const doris = zones.find((item) => item.zone.id === 'doris');
    const vm = zones.find((item) => item.zone.id === 'vm');
    if (doris && vm) {
      vm.x = doris.x + (vm.width - doris.width) / 2;
      doris.z = -(vm.depth + 2.4) / 2;
      vm.z = (doris.depth + 2.4) / 2;
    }
  }

  const minX = Math.min(...zones.map((zone) => zone.x - zone.width / 2));
  const maxX = Math.max(...zones.map((zone) => zone.x + zone.width / 2));
  const minZ = Math.min(...zones.map((zone) => zone.z - zone.depth / 2));
  const maxZ = Math.max(...zones.map((zone) => zone.z + zone.depth / 2));
  const centerX = (minX + maxX) / 2;
  const centerZ = (minZ + maxZ) / 2;
  for (const zone of zones) {
    zone.x -= centerX;
    zone.z -= centerZ;
  }

  return {
    nodes: zones.flatMap((zone) =>
      zone.members.map((node, index) => ({
        node,
        x:
          zone.x -
          ((zone.columns - 1) * 3.3) / 2 +
          (index % zone.columns) * 3.3,
        z:
          zone.z -
          zone.depth / 2 +
          3.3 +
          Math.floor(index / zone.columns) * 4.2,
      })),
    ),
    zones: zones.map(({ zone, x, z, width, depth }) => ({
      zone,
      x,
      z,
      width,
      depth,
    })),
    width: maxX - minX,
    depth: maxZ - minZ,
  };
}

export type TopologyLayout = ReturnType<typeof layoutTopology>;
