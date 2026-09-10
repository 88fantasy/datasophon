import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import { serviceIconFor } from '../ObservabilityCollector/serviceIcon';
import type { TopologyLayout } from './topologyLayout';
import { TOPOLOGY_ZONES, type TopologyNode, type TopologyTheme } from './types';

type ModelKind =
  | 'rack'
  | 'database'
  | 'storage'
  | 'container'
  | 'browser'
  | 'gateway';
type Surface = 'metal' | 'dark' | 'trim' | 'accent' | 'status' | 'screen';

const STATE_COLORS = {
  running: '#39dba5',
  warning: '#ffbb4d',
  stopped: '#fb7185',
  unknown: '#93a1b8',
};

function modelKind(node: TopologyNode): ModelKind {
  if (node.kind === 'host') return 'rack';
  if (node.kind === 'browser' || node.kind === 'gateway') return node.kind;
  const name = node.serviceName || node.label;
  if (
    /doris|mysql|postgres|redis|mongo|clickhouse|hbase|oracle|elasticsearch/i.test(
      name,
    )
  )
    return 'database';
  if (/rustfs|minio|nexus|harbor|hdfs|hadoop|ceph|storage/i.test(name))
    return 'storage';
  return 'container';
}

export function topologyModelHeight(node: TopologyNode) {
  return node.kind === 'host' ? 3.15 : node.kind === 'browser' ? 2.55 : 2.6;
}

/** Every repeated detail is merged by material, then instanced across machines. */
function modelGeometry(kind: ModelKind) {
  const parts = new Map<Surface, THREE.BufferGeometry[]>();
  const add = (
    surface: Surface,
    geometry: THREE.BufferGeometry,
    x: number,
    y: number,
    z: number,
    rx = 0,
    ry = 0,
    rz = 0,
  ) => {
    geometry.rotateX(rx).rotateY(ry).rotateZ(rz).translate(x, y, z);
    const normalized = geometry.index ? geometry.toNonIndexed() : geometry;
    if (normalized !== geometry) geometry.dispose();
    const bucket = parts.get(surface) || [];
    bucket.push(normalized);
    parts.set(surface, bucket);
  };
  const box = (
    surface: Surface,
    x: number,
    y: number,
    z: number,
    w: number,
    h: number,
    d: number,
    rounded = false,
  ) =>
    add(
      surface,
      rounded
        ? new RoundedBoxGeometry(w, h, d, 2, Math.min(0.075, h / 3))
        : new THREE.BoxGeometry(w, h, d),
      x,
      y,
      z,
    );
  const disc = (
    surface: Surface,
    x: number,
    y: number,
    z: number,
    r: number,
    h: number,
    front = false,
  ) =>
    add(
      surface,
      new THREE.CylinderGeometry(r, r, h, 32),
      x,
      y,
      z,
      front ? Math.PI / 2 : 0,
    );
  const ring = (
    surface: Surface,
    x: number,
    y: number,
    z: number,
    r: number,
    tube: number,
    flat = false,
  ) =>
    add(
      surface,
      new THREE.TorusGeometry(r, tube, 6, 24),
      x,
      y,
      z,
      flat ? Math.PI / 2 : 0,
    );

  box('trim', 0, 0.13, 0, 2.1, 0.22, 1.8, true);
  box('dark', 0, 0.035, 0, 1.7, 0.07, 1.45);
  for (const x of [-0.75, 0.75]) {
    for (const z of [-0.6, 0.6]) disc('dark', x, 0.19, z, 0.07, 0.09);
  }

  if (kind === 'rack') {
    box('metal', 0, 1.57, 0, 1.62, 2.65, 1.38, true);
    box('dark', 0, 1.58, 0.702, 1.4, 2.39, 0.045);
    box('accent', 0, 2.86, 0.727, 1.33, 0.045, 0.025);
    for (const x of [-0.75, 0.75]) {
      box('trim', x, 1.58, 0.742, 0.07, 2.45, 0.085);
      for (let row = 0; row < 15; row += 1)
        disc('dark', x, 0.43 + row * 0.158, 0.791, 0.018, 0.008, true);
    }
    for (let tray = 0; tray < 7; tray += 1) {
      const y = 0.55 + tray * 0.31;
      box('metal', 0, y, 0.746, 1.27, 0.245, 0.065, true);
      for (let vent = 0; vent < 12; vent += 1)
        box('dark', -0.51 + vent * 0.057, y, 0.784, 0.022, 0.15, 0.008);
      box('dark', 0.31, y, 0.786, 0.12, 0.08, 0.015);
      box('trim', 0.31, y - 0.025, 0.797, 0.09, 0.014, 0.01);
      disc('status', 0.52, y + 0.043, 0.79, 0.026, 0.012, true);
      disc('accent', 0.52, y - 0.04, 0.79, 0.019, 0.012, true);
    }
    // Rear cooling fans, ethernet bank and a visible side ventilation panel.
    for (const y of [0.87, 1.6, 2.33]) {
      disc('dark', 0, y, -0.699, 0.28, 0.015, true);
      ring('trim', 0, y, -0.714, 0.25, 0.022);
      disc('metal', 0, y, -0.73, 0.075, 0.023, true);
      for (let blade = 0; blade < 6; blade += 1) {
        const angle = (blade * Math.PI) / 3;
        add(
          'metal',
          new THREE.BoxGeometry(0.07, 0.17, 0.013),
          Math.sin(angle) * 0.135,
          y + Math.cos(angle) * 0.135,
          -0.724,
          0,
          0,
          -angle + 0.4,
        );
      }
    }
    for (let vent = 0; vent < 13; vent += 1) {
      box('dark', 0.814, 0.76 + vent * 0.13, 0, 0.012, 0.047, 0.96);
      box('dark', -0.814, 0.76 + vent * 0.13, 0, 0.012, 0.047, 0.96);
    }
    for (let port = 0; port < 4; port += 1)
      box('dark', -0.36 + port * 0.24, 2.69, -0.704, 0.13, 0.09, 0.018);
    box('trim', 0, 2.92, 0, 1.38, 0.035, 1.16);
    for (let vent = 0; vent < 9; vent += 1)
      box('dark', -0.47 + vent * 0.115, 2.944, 0, 0.032, 0.012, 0.77);
  } else if (kind === 'database') {
    disc('dark', 0, 0.35, 0, 0.84, 0.19);
    for (let layer = 0; layer < 4; layer += 1) {
      const y = 0.62 + layer * 0.48;
      disc('metal', 0, y, 0, 0.73, 0.36);
      disc('trim', 0, y + 0.19, 0, 0.77, 0.07);
      ring('accent', 0, y - 0.18, 0, 0.72, 0.028, true);
      box('dark', 0, y, 0.732, 0.38, 0.11, 0.045, true);
      for (let led = 0; led < 3; led += 1)
        disc('status', -0.11 + led * 0.11, y, 0.761, 0.019, 0.014, true);
    }
    for (const x of [-0.85, 0.85])
      box('trim', x, 1.32, -0.2, 0.08, 1.95, 0.13, true);
  } else if (kind === 'storage') {
    box('metal', 0, 1.25, 0, 1.82, 1.97, 1.3, true);
    box('dark', 0, 1.27, 0.659, 1.63, 1.74, 0.025);
    for (let row = 0; row < 4; row += 1) {
      for (let col = 0; col < 3; col += 1) {
        const x = -0.53 + col * 0.53;
        const y = 0.62 + row * 0.43;
        box('trim', x, y, 0.708, 0.45, 0.34, 0.085, true);
        box('dark', x, y + 0.024, 0.757, 0.32, 0.11, 0.015);
        box('metal', x, y - 0.085, 0.77, 0.31, 0.04, 0.045);
        disc('status', x + 0.145, y + 0.09, 0.774, 0.018, 0.01, true);
      }
    }
    box('accent', 0, 2.21, 0, 1.67, 0.035, 1.13);
    for (let stripe = 0; stripe < 9; stripe += 1)
      box('dark', 0.919, 0.57 + stripe * 0.17, 0, 0.015, 0.045, 0.95);
    for (const x of [-0.46, 0.46]) {
      disc('dark', x, 1.38, -0.66, 0.32, 0.025, true);
      ring('trim', x, 1.38, -0.684, 0.27, 0.025);
    }
  } else if (kind === 'container') {
    box('metal', 0, 1.24, 0, 1.63, 1.84, 1.27, true);
    for (const x of [-0.79, 0.79]) {
      for (const z of [-0.6, 0.6]) box('trim', x, 1.23, z, 0.13, 1.84, 0.13);
    }
    for (let layer = 0; layer < 3; layer += 1) {
      const y = 0.64 + layer * 0.56;
      box('dark', 0, y, 0.648, 1.3, 0.45, 0.035);
      box('accent', -0.43, y, 0.69, 0.3, 0.31, 0.07, true);
      for (let port = 0; port < 3; port += 1)
        box('trim', -0.07 + port * 0.23, y, 0.683, 0.15, 0.16, 0.03, true);
      disc('status', 0.56, y + 0.15, 0.681, 0.025, 0.02, true);
      for (let stripe = 0; stripe < 5; stripe += 1)
        box('dark', 0.821, y - 0.16 + stripe * 0.071, 0, 0.015, 0.025, 0.89);
    }
    box('trim', 0, 2.22, 0, 1.81, 0.12, 1.45, true);
  } else if (kind === 'browser') {
    box('metal', 0, 0.98, -0.1, 0.16, 1.45, 0.17, true);
    box('metal', 0, 1.65, -0.13, 1.95, 1.29, 0.15, true);
    box('dark', 0, 1.68, -0.038, 1.8, 1.09, 0.04, true);
    box('screen', 0, 1.68, -0.009, 1.68, 0.97, 0.015);
    box('metal', 0, 2.095, 0.004, 1.67, 0.14, 0.014);
    for (let dot = 0; dot < 3; dot += 1)
      disc('accent', -0.72 + dot * 0.1, 2.097, 0.016, 0.023, 0.008, true);
    box('trim', 0.13, 2.095, 0.018, 1.05, 0.06, 0.012, true);
    box('accent', -0.54, 1.67, 0.016, 0.27, 0.48, 0.012);
    for (let line = 0; line < 3; line += 1)
      box(
        'metal',
        0.23,
        1.85 - line * 0.18,
        0.02,
        0.83 - line * 0.16,
        0.055,
        0.012,
      );
    box('dark', 0, 0.3, 0.53, 1.45, 0.07, 0.43, true);
    for (let row = 0; row < 3; row += 1) {
      for (let key = 0; key < 11; key += 1)
        box(
          'trim',
          -0.59 + key * 0.116,
          0.349,
          0.41 + row * 0.105,
          0.078,
          0.019,
          0.066,
        );
    }
    box('accent', 0, 0.39, -0.05, 0.67, 0.045, 0.45, true);
    disc('status', 0.7, 1.089, 0.012, 0.02, 0.014, true);
  } else {
    box('metal', 0, 1.17, 0, 1.42, 1.82, 1.25, true);
    box('dark', 0, 1.17, 0.646, 1.21, 1.58, 0.055);
    for (let slot = 0; slot < 4; slot += 1) {
      const y = 0.52 + slot * 0.32;
      box('trim', 0, y, 0.692, 1.06, 0.21, 0.05, true);
      for (let port = 0; port < 4; port += 1) {
        box('dark', -0.38 + port * 0.2, y, 0.721, 0.12, 0.105, 0.013);
        box('status', -0.37 + port * 0.2, y + 0.07, 0.726, 0.031, 0.022, 0.01);
      }
    }
    box('accent', 0, 2.12, 0, 1.24, 0.06, 1.08, true);
    // An actual extruded shield, mounted above the gateway front panel.
    const shape = new THREE.Shape();
    shape.moveTo(-0.4, 0.31);
    shape.lineTo(0, 0.45);
    shape.lineTo(0.4, 0.31);
    shape.lineTo(0.34, -0.18);
    shape.quadraticCurveTo(0.22, -0.42, 0, -0.55);
    shape.quadraticCurveTo(-0.22, -0.42, -0.34, -0.18);
    shape.closePath();
    add(
      'accent',
      new THREE.ExtrudeGeometry(shape, {
        depth: 0.09,
        bevelEnabled: true,
        bevelSize: 0.024,
        bevelThickness: 0.02,
        bevelSegments: 2,
        steps: 1,
      }),
      0.5,
      1.97,
      0.79,
    );
    ring('trim', 0.5, 1.96, 0.92, 0.2, 0.019);
    box('trim', 0.5, 1.96, 0.924, 0.025, 0.35, 0.01);
    box('trim', 0.5, 1.96, 0.924, 0.35, 0.025, 0.01);
  }

  return Array.from(parts, ([surface, geometries]) => {
    const geometry = mergeGeometries(geometries);
    for (const part of geometries) part.dispose();
    if (!geometry) throw new Error('无法生成拓扑模型');
    geometry.computeBoundingSphere();
    return { surface, geometry };
  });
}

export function createTopologyModels(
  positions: TopologyLayout['nodes'],
  theme: TopologyTheme,
  onIconLoad?: () => void,
) {
  const group = new THREE.Group();
  const materials: Record<Surface, THREE.MeshStandardMaterial> = {
    metal: new THREE.MeshStandardMaterial({
      color: '#b5c3d3',
      metalness: 0.68,
      roughness: 0.3,
    }),
    dark: new THREE.MeshStandardMaterial({
      color: '#162638',
      metalness: 0.45,
      roughness: 0.52,
    }),
    trim: new THREE.MeshStandardMaterial({
      color: '#e0e9f5',
      metalness: 0.8,
      roughness: 0.22,
    }),
    accent: new THREE.MeshStandardMaterial({
      color: '#ffffff',
      metalness: 0.38,
      roughness: 0.28,
    }),
    status: new THREE.MeshStandardMaterial({
      color: '#ffffff',
      emissive: '#ffffff',
      emissiveIntensity: 0.55,
      roughness: 0.3,
    }),
    screen: new THREE.MeshStandardMaterial({
      color: '#24415e',
      emissive: '#142e44',
      emissiveIntensity: 0.5,
      metalness: 0.2,
      roughness: 0.21,
    }),
  };
  const transform = new THREE.Object3D();
  const zoneColors = new Map(
    TOPOLOGY_ZONES.map((zone) => [zone.id, new THREE.Color(zone.color)]),
  );
  const kinds = new Set(positions.map(({ node }) => modelKind(node)));
  for (const kind of kinds) {
    const members = positions.filter(({ node }) => modelKind(node) === kind);
    for (const { surface, geometry } of modelGeometry(kind)) {
      const mesh = new THREE.InstancedMesh(
        geometry,
        materials[surface],
        members.length,
      );
      members.forEach(({ node, x, z }, index) => {
        transform.position.set(x, 0.29, z);
        transform.updateMatrix();
        mesh.setMatrixAt(index, transform.matrix);
        if (surface === 'accent')
          mesh.setColorAt(
            index,
            zoneColors.get(node.zone) || new THREE.Color('#4385f5'),
          );
        if (surface === 'status')
          mesh.setColorAt(index, new THREE.Color(STATE_COLORS[node.state]));
      });
      mesh.castShadow = surface !== 'status';
      mesh.receiveShadow = true;
      group.add(mesh);
    }
  }
  const iconMaterials = new Map<string, THREE.MeshBasicMaterial>();
  const loader = new THREE.TextureLoader();
  for (const { node, x, z } of positions) {
    if (!node.serviceName) continue;
    const { src } = serviceIconFor(node.serviceName);
    let material = iconMaterials.get(src);
    if (!material) {
      const texture = loader.load(src, onIconLoad, undefined, (error) => {
        console.error('拓扑服务图标加载失败', node.serviceName, error);
      });
      texture.colorSpace = THREE.SRGBColorSpace;
      material = new THREE.MeshBasicMaterial({
        map: texture,
        transparent: true,
        toneMapped: false,
      });
      iconMaterials.set(src, material);
    }
    const icon = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1).rotateX(-Math.PI / 2),
      material,
    );
    icon.name = `service-icon:${node.id}`;
    icon.position.set(x, modelKind(node) === 'storage' ? 2.535 : 2.59, z);
    group.add(icon);
  }
  // Dispose even unused palette entries together with the group.
  group.userData.materials = Object.values(materials);

  const hitboxes = new THREE.InstancedMesh(
    new THREE.BoxGeometry(2.12, 1, 1.9),
    new THREE.MeshBasicMaterial({ visible: false }),
    positions.length,
  );
  const markers = new THREE.InstancedMesh(
    new THREE.RingGeometry(1.22, 1.35, 48).rotateX(-Math.PI / 2),
    new THREE.MeshBasicMaterial({
      color: '#ffffff',
      transparent: true,
      opacity: theme === 'dark' ? 0.94 : 0.8,
      depthWrite: false,
    }),
    positions.length,
  );
  positions.forEach(({ node, x, z }, index) => {
    const height = topologyModelHeight(node);
    transform.position.set(x, height / 2 + 0.29, z);
    transform.scale.set(1, height, 1);
    transform.updateMatrix();
    hitboxes.setMatrixAt(index, transform.matrix);
    transform.position.set(x, 0.31, z);
    transform.scale.set(0, 0, 0);
    transform.updateMatrix();
    markers.setMatrixAt(index, transform.matrix);
  });
  hitboxes.userData.nodeIds = positions.map(({ node }) => node.id);
  hitboxes.computeBoundingSphere();
  markers.frustumCulled = false;
  group.add(hitboxes, markers);
  return { group, hitboxes, markers };
}
