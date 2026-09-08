import * as THREE from 'three';
import { describe, expect, it } from 'vitest';
import { layoutTopology } from './topologyLayout';
import { createTopologyModels } from './topologyModels';
import { TOPOLOGY_ZONES, type TopologyNode } from './types';

const nodes: TopologyNode[] = Array.from({ length: 100 }, (_, index) => ({
  id: `host-${index}`,
  label: `server-${index}`,
  subtitle: '172.17.9.1',
  zone: TOPOLOGY_ZONES[index % TOPOLOGY_ZONES.length].id,
  kind: 'host',
  source: 'inventory',
  state: 'running',
  details: [],
  instances: [],
}));

describe('topology world layout', () => {
  it('retains 100 servers inside disjoint zones with room for models and labels', () => {
    const layout = layoutTopology(nodes, null);
    expect(layout.nodes).toHaveLength(100);
    for (const position of layout.nodes) {
      const zone = layout.zones.find(
        (item) => item.zone.id === position.node.zone,
      );
      expect(zone).toBeDefined();
      if (!zone) throw new Error('missing zone');
      expect(Math.abs(position.x - zone.x) + 1.1).toBeLessThan(zone.width / 2);
      expect(Math.abs(position.z - zone.z) + 1.1).toBeLessThan(zone.depth / 2);
      for (const other of layout.nodes) {
        if (other.node.id === position.node.id) continue;
        expect(
          Math.hypot(other.x - position.x, other.z - position.z),
        ).toBeGreaterThan(3.2);
      }
    }
    layout.zones.forEach((zone, index) => {
      for (const other of layout.zones.slice(index + 1)) {
        expect(
          Math.abs(zone.x - other.x) >= (zone.width + other.width) / 2 ||
            Math.abs(zone.z - other.z) >= (zone.depth + other.depth) / 2,
        ).toBe(true);
      }
    });
  });

  it('focuses a single zone without mutating data and remains stable on refresh order', () => {
    const focused = layoutTopology(nodes, 'k8s');
    expect(focused.zones.map((item) => item.zone.id)).toEqual(['k8s']);
    expect(focused.nodes).toHaveLength(20);
    expect(focused.nodes.every(({ node }) => node.zone === 'k8s')).toBe(true);
    expect(layoutTopology([...nodes].reverse(), 'k8s')).toEqual(focused);
    expect(layoutTopology([], null).zones).toHaveLength(0);
    expect(layoutTopology([], 'vm').nodes).toEqual([]);
  });

  it('hides empty zones and keeps VM disjoint when Doris is absent', () => {
    const sparse = layoutTopology(
      nodes.filter((node) => ['edge', 'vm', 'other'].includes(node.zone)),
      null,
    );
    expect(sparse.zones.map(({ zone }) => zone.id)).toEqual([
      'edge',
      'vm',
      'other',
    ]);
    sparse.zones.forEach((zone, index) => {
      for (const other of sparse.zones.slice(index + 1)) {
        expect(
          Math.abs(zone.x - other.x) >= (zone.width + other.width) / 2 ||
            Math.abs(zone.z - other.z) >= (zone.depth + other.depth) / 2,
        ).toBe(true);
      }
    });
    for (const focus of [null, 'vm'] as const) {
      const empty = layoutTopology([], focus);
      expect(empty.zones).toEqual([]);
      expect(empty.nodes).toEqual([]);
      expect(Number.isFinite(empty.width) && empty.width > 0).toBe(true);
      expect(Number.isFinite(empty.depth) && empty.depth > 0).toBe(true);
    }
  });

  it('lays out 100 machines in one zone and picks the correct detailed, instanced model', () => {
    const layout = layoutTopology(
      nodes.map((node) => ({ ...node, zone: 'k8s' })),
      'k8s',
    );
    expect(layout.nodes).toHaveLength(100);
    expect(new Set(layout.nodes.map(({ x, z }) => `${x},${z}`)).size).toBe(100);
    const distances = layout.nodes.flatMap((position, index) =>
      layout.nodes
        .slice(index + 1)
        .map((other) => Math.hypot(position.x - other.x, position.z - other.z)),
    );
    expect(Math.min(...distances)).toBeGreaterThan(3.2);
    const models = createTopologyModels(layout.nodes, 'dark');
    try {
      expect(models.group.children.length).toBeLessThanOrEqual(8);
      expect(models.hitboxes.count).toBe(100);
      const totalVertices = models.group.children.reduce(
        (total, object) =>
          total +
          (object instanceof THREE.InstancedMesh
            ? object.geometry.getAttribute('position').count
            : 0),
        0,
      );
      expect(totalVertices).toBeGreaterThan(1000);
      models.group.updateMatrixWorld(true);
      const position = layout.nodes[71];
      const raycaster = new THREE.Raycaster(
        new THREE.Vector3(position.x, 10, position.z),
        new THREE.Vector3(0, -1, 0),
      );
      const hit = raycaster.intersectObject(models.hitboxes, false)[0];
      expect(hit?.instanceId).toBe(71);
      expect(models.hitboxes.userData.nodeIds[hit.instanceId ?? -1]).toBe(
        position.node.id,
      );
    } finally {
      const materials = new Set<THREE.Material>(
        models.group.userData.materials,
      );
      models.group.traverse((object) => {
        if (!(object instanceof THREE.InstancedMesh)) return;
        object.dispose();
        object.geometry.dispose();
        for (const material of Array.isArray(object.material)
          ? object.material
          : [object.material])
          materials.add(material);
      });
      for (const material of materials) material.dispose();
    }
  });
});
