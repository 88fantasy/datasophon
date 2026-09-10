import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { layoutTopology } from './topologyLayout';
import { createTopologyModels, topologyModelHeight } from './topologyModels';
import type { TopologyMode, TopologySceneProps, ZoneId } from './types';

type CameraView = {
  target: THREE.Vector3;
  direction: THREE.Vector3;
  span: number;
};
type SceneRuntime = {
  isFailed: () => boolean;
  setMode: (mode: TopologyMode) => void;
  fit: () => void;
  highlight: () => void;
  dispose: () => void;
};
type SceneLabel = {
  element: HTMLButtonElement;
  position: THREE.Vector3;
  nodeId?: string;
  zone?: ZoneId;
};

const getLabelWidth = (zone?: ZoneId) =>
  zone === 'edge' ? 90 : zone ? 120 : 126;

function mountScene(
  host: HTMLDivElement,
  current: { current: TopologySceneProps },
  views: Map<string, CameraView>,
): SceneRuntime {
  const { nodes, edges, focusZone, theme } = current.current;
  const dark = theme === 'dark';
  const layout = layoutTopology(nodes, focusZone);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(dark ? '#0b1424' : '#edf2f8');
  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: false,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = dark ? 1.15 : 1.2;
  const canvas = renderer.domElement;
  canvas.style.cssText =
    'width:100%;height:100%;display:block;outline:none;touch-action:none';
  canvas.tabIndex = 0;
  canvas.setAttribute('role', 'img');
  canvas.setAttribute(
    'aria-label',
    '集群三维部署拓扑。拖动旋转，滚轮缩放，右键拖动或方向键平移。资源也可通过模型上方的按钮选择。',
  );
  const overlay = document.createElement('div');
  overlay.style.cssText =
    'position:absolute;inset:0;pointer-events:none;overflow:hidden';
  host.replaceChildren(canvas, overlay);

  let camera: THREE.PerspectiveCamera | THREE.OrthographicCamera;
  let controls: OrbitControls | undefined;
  let activeMode: TopologyMode = current.current.mode;
  let width = Math.max(host.clientWidth, 1);
  let height = Math.max(host.clientHeight, 1);
  let frame = 0;
  let disposed = false;
  let failed = false;
  let paused = false;
  let hoveredId: string | null = null;
  let environment: THREE.WebGLRenderTarget | undefined;
  let observer: ResizeObserver | undefined;
  const labels: SceneLabel[] = [];
  const scope = current.current.scopeKey || focusZone || 'overview';
  const extent = Math.max(layout.width, layout.depth, 12);
  let perspectiveDirection =
    views.get(`${scope}:3d`)?.direction.clone() ||
    new THREE.Vector3(0.26, 0.88, 1.12).normalize();
  const meshes: ReturnType<typeof createTopologyModels>[] = [];
  const zoneMeshes: THREE.Mesh[] = [];
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  let pointerStart: { x: number; y: number } | null = null;

  const fittedSpan = () =>
    Math.max(
      layout.depth + (focusZone ? 2 : 4),
      (layout.width + 4) / (width / height),
    ) * 1.08;

  const view = (): CameraView => ({
    target: controls?.target.clone() || new THREE.Vector3(),
    direction: camera.position
      .clone()
      .sub(controls?.target || new THREE.Vector3())
      .normalize(),
    span:
      camera instanceof THREE.OrthographicCamera
        ? (camera.top - camera.bottom) / camera.zoom
        : 2 *
          camera.position.distanceTo(controls?.target || new THREE.Vector3()) *
          Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)),
  });

  const error = (message: string) => {
    if (failed || disposed) return;
    failed = true;
    current.current.onError(message);
  };

  const updateLabels = () => {
    const { selectedId, highlightedIds } = current.current;
    const highlighted = new Set(highlightedIds || []);
    const order = [...labels].sort((a, b) => {
      const priority = (label: SceneLabel) =>
        label.nodeId === selectedId || label.nodeId === hoveredId
          ? 4
          : label.zone
            ? 3
            : label.nodeId && highlighted.has(label.nodeId)
              ? 2
              : 0;
      return priority(b) - priority(a);
    });
    // ponytail: pairwise label culling covers hundreds of nodes; use screen bins for thousands.
    const occupied: {
      left: number;
      top: number;
      right: number;
      bottom: number;
    }[] = [];
    for (const label of order) {
      const projected = label.position.clone().project(camera);
      const x = (projected.x * 0.5 + 0.5) * width;
      const y = (-projected.y * 0.5 + 0.5) * height;
      const labelWidth = label.zone
        ? getLabelWidth(label.zone)
        : label.element.dataset.compact
          ? 104
          : 126;
      const labelHeight = label.zone
        ? 44
        : label.element.dataset.compact
          ? 25
          : 35;
      const bounds = {
        left: x - labelWidth / 2,
        right: x + labelWidth / 2,
        top: y - labelHeight,
        bottom: y + 2,
      };
      const important =
        label.nodeId === selectedId || label.nodeId === hoveredId;
      const collision = occupied.some(
        (other) =>
          bounds.left < other.right + 3 &&
          bounds.right > other.left - 3 &&
          bounds.top < other.bottom + 3 &&
          bounds.bottom > other.top - 3,
      );
      const hidden =
        projected.z < -1 ||
        projected.z > 1 ||
        x < 0 ||
        x > width ||
        y < 0 ||
        y > height ||
        (!important && collision);
      label.element.style.display = hidden ? 'none' : 'block';
      if (hidden) continue;
      occupied.push(bounds);
      label.element.style.transform = `translate(${x}px,${y}px) translate(-50%,-100%)`;
      label.element.style.zIndex = important ? '3' : label.zone ? '2' : '1';
      if (label.nodeId) {
        const matching = highlighted.has(label.nodeId);
        label.element.style.borderColor = important
          ? '#62a3ff'
          : matching
            ? '#ffbc52'
            : dark
              ? '#32435b'
              : '#d8e1ee';
        label.element.style.boxShadow = important
          ? '0 0 0 2px #4385f54d,0 5px 18px #0002'
          : matching
            ? '0 0 0 2px #ffbc524d'
            : '0 3px 9px #0000000b';
        label.element.style.opacity =
          highlightedIds && !matching && !important ? '0.56' : '1';
      }
    }
  };

  const render = () => {
    frame = 0;
    if (
      disposed ||
      failed ||
      paused ||
      document.hidden ||
      !camera ||
      !host.clientWidth ||
      !host.clientHeight
    )
      return;
    try {
      renderer.render(scene, camera);
      updateLabels();
    } catch {
      error('3D 场景渲染失败，已切换至 2D 平面视图。');
    }
  };
  const invalidate = () => {
    if (!frame && !disposed && !failed && !paused)
      frame = requestAnimationFrame(render);
  };

  const setMode = (mode: TopologyMode) => {
    if (mode === '2d') {
      paused = true;
      if (controls) controls.enabled = false;
      cancelAnimationFrame(frame);
      frame = 0;
      return;
    }
    paused = false;
    if (camera && activeMode === mode) {
      if (controls) controls.enabled = true;
      invalidate();
      return;
    }
    const previous = camera ? view() : views.get(`${scope}:${mode}`);
    if (camera) {
      views.set(`${scope}:${activeMode}`, view());
      if (activeMode === '3d') perspectiveDirection = view().direction;
    }
    activeMode = mode;
    controls?.dispose();
    const span = previous?.span || fittedSpan();
    const target = previous?.target || new THREE.Vector3(0, 0.45, 0);
    const distance = span / (2 * Math.tan(THREE.MathUtils.degToRad(20)));
    camera =
      mode === '3d'
        ? new THREE.PerspectiveCamera(
            40,
            width / height,
            0.1,
            Math.max(2000, extent * 25),
          )
        : new THREE.OrthographicCamera(
            (-span * width) / height / 2,
            (span * width) / height / 2,
            span / 2,
            -span / 2,
            0.1,
            Math.max(2000, extent * 25),
          );
    camera.position
      .copy(target)
      .addScaledVector(
        mode === '3d'
          ? perspectiveDirection
          : new THREE.Vector3(0.18, 1, 0.43).normalize(),
        distance,
      );
    controls = new OrbitControls(camera, canvas);
    controls.target.copy(target);
    controls.enableDamping = false;
    controls.enableRotate = mode === '3d';
    controls.screenSpacePanning = true;
    controls.minDistance = 3;
    controls.maxDistance = extent * 10;
    controls.minZoom = 0.2;
    controls.maxZoom = 25;
    controls.maxPolarAngle = Math.PI / 2 - 0.06;
    if (mode !== '3d') controls.mouseButtons.LEFT = THREE.MOUSE.PAN;
    controls.listenToKeyEvents(canvas);
    controls.addEventListener('change', invalidate);
    controls.update();
    invalidate();
  };

  const fit = () => {
    if (!camera || !controls) return;
    const span = fittedSpan();
    const direction =
      activeMode === '3d'
        ? new THREE.Vector3(0.26, 0.88, 1.12).normalize()
        : new THREE.Vector3(0.18, 1, 0.43).normalize();
    controls.target.set(0, 0.45, 0);
    camera.position
      .copy(controls.target)
      .addScaledVector(
        direction,
        span / (2 * Math.tan(THREE.MathUtils.degToRad(20))),
      );
    if (camera instanceof THREE.OrthographicCamera) {
      camera.left = (-span * width) / height / 2;
      camera.right = -camera.left;
      camera.top = span / 2;
      camera.bottom = -span / 2;
      camera.zoom = 1;
      camera.updateProjectionMatrix();
    }
    controls.update();
    invalidate();
  };

  const highlight = () => {
    const selected = current.current.selectedId;
    const highlighted = new Set(current.current.highlightedIds || []);
    const transform = new THREE.Object3D();
    for (const model of meshes) {
      layout.nodes.forEach(({ node, x, z }, index) => {
        const active = selected === node.id || hoveredId === node.id;
        const visible = active || highlighted.has(node.id);
        transform.position.set(x, 0.31, z);
        transform.scale.setScalar(visible ? 1 : 0);
        transform.updateMatrix();
        model.markers.setMatrixAt(index, transform.matrix);
        model.markers.setColorAt(
          index,
          new THREE.Color(active ? '#5b9eff' : '#ffbc52'),
        );
      });
      model.markers.instanceMatrix.needsUpdate = true;
      if (model.markers.instanceColor)
        model.markers.instanceColor.needsUpdate = true;
    }
    invalidate();
  };

  const pick = (event: MouseEvent | PointerEvent) => {
    const rect = canvas.getBoundingClientRect();
    pointer.set(
      ((event.clientX - rect.left) / rect.width) * 2 - 1,
      -((event.clientY - rect.top) / rect.height) * 2 + 1,
    );
    raycaster.setFromCamera(pointer, camera);
    const hit = raycaster.intersectObjects(
      meshes.map((model) => model.hitboxes),
      false,
    )[0];
    return hit?.instanceId === undefined
      ? null
      : (hit.object.userData.nodeIds[hit.instanceId] as string);
  };
  const onPointerDown = (event: PointerEvent) => {
    pointerStart = { x: event.clientX, y: event.clientY };
  };
  const onPointerUp = (event: PointerEvent) => {
    if (event.button !== 0 || !pointerStart) return;
    const moved = Math.hypot(
      event.clientX - pointerStart.x,
      event.clientY - pointerStart.y,
    );
    pointerStart = null;
    if (moved < 5) {
      const id = pick(event);
      if (id) current.current.onSelect(id);
      else if (!focusZone) {
        const zone = raycaster.intersectObjects(zoneMeshes, false)[0]?.object
          .userData.zone;
        if (zone) current.current.onEnterZone(zone);
      } else current.current.onSelect(null);
    }
  };
  const onPointerMove = (event: PointerEvent) => {
    if (event.buttons) return;
    const next = pick(event);
    canvas.style.cursor = next
      ? 'pointer'
      : activeMode === '3d'
        ? 'grab'
        : 'move';
    if (next !== hoveredId) {
      hoveredId = next;
      highlight();
    }
  };
  const onPointerLeave = () => {
    hoveredId = null;
    pointerStart = null;
    highlight();
  };
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') current.current.onSelect(null);
  };
  const onContextLost = (event: Event) => {
    event.preventDefault();
    error('浏览器的 3D 上下文已丢失，已切换至 2D 平面视图。');
  };

  const dispose = () => {
    if (disposed) return;
    disposed = true;
    if (camera) views.set(`${scope}:${activeMode}`, view());
    cancelAnimationFrame(frame);
    observer?.disconnect();
    controls?.dispose();
    canvas.removeEventListener('pointerdown', onPointerDown);
    canvas.removeEventListener('pointerup', onPointerUp);
    canvas.removeEventListener('pointermove', onPointerMove);
    canvas.removeEventListener('pointerleave', onPointerLeave);
    canvas.removeEventListener('keydown', onKeyDown);
    canvas.removeEventListener('webglcontextlost', onContextLost);
    document.removeEventListener('visibilitychange', invalidate);
    const geometries = new Set<THREE.BufferGeometry>();
    const materials = new Set<THREE.Material>();
    scene.traverse((object) => {
      if (
        object instanceof THREE.Mesh ||
        object instanceof THREE.LineSegments ||
        object instanceof THREE.Line
      ) {
        geometries.add(object.geometry);
        for (const material of Array.isArray(object.material)
          ? object.material
          : [object.material])
          materials.add(material);
      }
      if (object instanceof THREE.InstancedMesh) object.dispose();
      if (object instanceof THREE.DirectionalLight) object.shadow.dispose();
      for (const material of object.userData.materials || [])
        materials.add(material);
    });
    for (const geometry of geometries) geometry.dispose();
    const textures = new Set<THREE.Texture>();
    for (const material of materials) {
      if (material instanceof THREE.MeshBasicMaterial && material.map)
        textures.add(material.map);
      material.dispose();
    }
    for (const texture of textures) texture.dispose();
    environment?.dispose();
    scene.clear();
    renderer.dispose();
    renderer.forceContextLoss();
    host.replaceChildren();
  };

  try {
    const room = new RoomEnvironment();
    const pmrem = new THREE.PMREMGenerator(renderer);
    try {
      environment = pmrem.fromScene(room, 0.05);
    } finally {
      room.dispose();
      pmrem.dispose();
    }
    scene.environment = environment.texture;
    scene.environmentIntensity = dark ? 0.65 : 0.8;
    scene.add(
      new THREE.HemisphereLight('#dcecff', dark ? '#334567' : '#a5b1c8', 1.9),
    );
    const sun = new THREE.DirectionalLight('#ffffff', 3.5);
    sun.position.set(-extent / 3, extent, extent / 2);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    sun.shadow.camera.left = -extent * 0.8;
    sun.shadow.camera.right = extent * 0.8;
    sun.shadow.camera.top = extent * 0.8;
    sun.shadow.camera.bottom = -extent * 0.8;
    sun.shadow.camera.far = extent * 4;
    sun.shadow.normalBias = 0.035;
    sun.shadow.bias = -0.0001;
    scene.add(sun);
    const fill = new THREE.DirectionalLight('#83bdff', 1.4);
    fill.position.set(extent, extent / 2, -extent / 2);
    scene.add(fill);
    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(extent * 4, extent * 4),
      new THREE.MeshBasicMaterial({
        color: dark ? '#0b1424' : '#edf2f8',
        toneMapped: false,
      }),
    );
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = -0.2;
    floor.receiveShadow = true;
    scene.add(floor);
    const grid = new THREE.GridHelper(
      Math.ceil(extent * 1.5),
      Math.ceil(extent * 1.5),
      dark ? '#21324b' : '#d0dae8',
      dark ? '#17273e' : '#e0e7f0',
    );
    grid.position.y = -0.18;
    scene.add(grid);

    const makeLabel = (
      text: string,
      subtitle: string,
      position: THREE.Vector3,
      nodeId?: string,
      zone?: ZoneId,
      compact = false,
    ) => {
      const button = document.createElement('button');
      button.type = 'button';
      if (compact) button.dataset.compact = 'true';
      button.title = `${text} · ${subtitle}${zone ? ' · 点击进入分区' : ''}`;
      button.style.cssText = `position:absolute;left:0;top:0;pointer-events:auto;cursor:pointer;padding:${zone ? '6px 10px' : '4px 7px'};width:${compact ? 104 : getLabelWidth(zone)}px;border:1px solid ${dark ? '#32435b' : '#d8e1ee'};border-radius:${zone ? 9 : 6}px;background:${dark ? '#142137ed' : '#fffffff0'};color:${dark ? '#e2ecf9' : '#263b59'};font-family:inherit;text-align:center;line-height:1.35;box-shadow:0 3px 9px #0000000b`;
      const title = document.createElement('span');
      title.style.cssText = `display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:${zone ? 12 : 11}px;font-weight:${zone ? 700 : 600}`;
      title.textContent = text;
      const detail = document.createElement('span');
      detail.style.cssText = `display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px;color:${dark ? '#91a6c2' : '#7b8da5'};margin-top:2px`;
      detail.textContent = subtitle;
      button.append(title);
      if (!compact) button.append(detail);
      button.onclick = () => {
        if (zone) current.current.onEnterZone(zone);
        else if (nodeId) current.current.onSelect(nodeId);
      };
      overlay.append(button);
      labels.push({ element: button, position, nodeId, zone });
    };

    for (const { zone, x, z, width: zoneWidth, depth } of layout.zones) {
      const geometry = new RoundedBoxGeometry(zoneWidth, 0.28, depth, 3, 0.13);
      const base = new THREE.Mesh(
        geometry,
        new THREE.MeshStandardMaterial({
          color: dark ? '#17283f' : '#e0e8f3',
          metalness: 0.15,
          roughness: 0.64,
        }),
      );
      base.position.set(x, 0.12, z);
      base.receiveShadow = true;
      base.userData.zone = zone.id;
      zoneMeshes.push(base);
      scene.add(base);
      const borderShape = new THREE.BoxGeometry(
        zoneWidth - 0.2,
        0.04,
        depth - 0.2,
      );
      const borderGeometry = new THREE.EdgesGeometry(borderShape);
      borderShape.dispose();
      const border = new THREE.LineSegments(
        borderGeometry,
        new THREE.LineBasicMaterial({
          color: zone.color,
          transparent: true,
          opacity: dark ? 0.62 : 0.45,
        }),
      );
      border.position.set(x, 0.27, z);
      scene.add(border);
      const stripe = new THREE.Mesh(
        new THREE.BoxGeometry(zoneWidth - 0.6, 0.04, 0.04),
        new THREE.MeshStandardMaterial({
          color: zone.color,
          emissive: zone.color,
          emissiveIntensity: 0.3,
        }),
      );
      stripe.position.set(x, 0.14, z + depth / 2 + 0.008);
      scene.add(stripe);
      const count = layout.nodes.filter(
        ({ node }) => node.zone === zone.id,
      ).length;
      makeLabel(
        zone.label,
        nodes.find((node) => node.zone === zone.id && node.kind === 'cluster')
          ?.subtitle ||
          (count
            ? `${count} 个资源 · ${zone.subtitle}`
            : '暂无资源 · 点击浏览分区'),
        new THREE.Vector3(x, 0.8, z + depth / 2 - 0.5),
        undefined,
        zone.id,
      );
    }
    const models = createTopologyModels(layout.nodes, theme, invalidate);
    meshes.push(models);
    scene.add(models.group);
    for (const { node, x, z } of layout.nodes) {
      makeLabel(
        node.label,
        node.subtitle,
        new THREE.Vector3(x, topologyModelHeight(node) + 0.55, z),
        node.id,
        undefined,
        node.kind === 'host',
      );
    }
    const positions = new Map(
      layout.nodes.map((position) => [position.node.id, position]),
    );
    for (const kind of ['deployment', 'schematic'] as const) {
      const points: THREE.Vector3[] = [];
      for (const edge of edges.filter((item) => item.kind === kind)) {
        const source = positions.get(edge.source);
        const target = positions.get(edge.target);
        if (!source || !target) continue;
        const middle = (source.x + target.x) / 2;
        const path = [
          new THREE.Vector3(source.x, 0.33, source.z),
          new THREE.Vector3(middle, 0.33, source.z),
          new THREE.Vector3(middle, 0.33, target.z),
          new THREE.Vector3(target.x, 0.33, target.z),
        ];
        for (let index = 0; index < path.length - 1; index += 1)
          points.push(path[index], path[index + 1]);
      }
      if (!points.length) continue;
      const material =
        kind === 'schematic'
          ? new THREE.LineDashedMaterial({
              color: dark ? '#c5a969' : '#a48441',
              transparent: true,
              opacity: 0.78,
              dashSize: 0.25,
              gapSize: 0.18,
            })
          : new THREE.LineBasicMaterial({
              color: dark ? '#6a95ca' : '#6a8caf',
              transparent: true,
              opacity: 0.48,
            });
      const lines = new THREE.LineSegments(
        new THREE.BufferGeometry().setFromPoints(points),
        material,
      );
      if (kind === 'schematic') lines.computeLineDistances();
      scene.add(lines);
    }
    scene.updateMatrixWorld(true);
    setMode(activeMode);
    highlight();
    const resize = () => {
      if (!host.clientWidth || !host.clientHeight) return;
      width = host.clientWidth;
      height = host.clientHeight;
      renderer.setSize(width, height, false);
      if (camera instanceof THREE.PerspectiveCamera)
        camera.aspect = width / height;
      else {
        camera.left = (-camera.top * width) / height;
        camera.right = -camera.left;
      }
      camera.updateProjectionMatrix();
      invalidate();
    };
    observer = new ResizeObserver(resize);
    observer.observe(host);
    resize();
    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointerup', onPointerUp);
    canvas.addEventListener('pointermove', onPointerMove);
    canvas.addEventListener('pointerleave', onPointerLeave);
    canvas.addEventListener('keydown', onKeyDown);
    canvas.addEventListener('webglcontextlost', onContextLost);
    document.addEventListener('visibilitychange', invalidate);
    return { isFailed: () => failed, setMode, fit, highlight, dispose };
  } catch (cause) {
    dispose();
    throw cause;
  }
}

export default function TopologyScene(props: TopologySceneProps) {
  const host = useRef<HTMLDivElement>(null);
  const current = useRef(props);
  current.current = props;
  const runtime = useRef<SceneRuntime | null>(null);
  const views = useRef(new Map<string, CameraView>());
  const builtFrom = useRef<TopologySceneProps | null>(null);
  useEffect(() => {
    if (!host.current) return;
    if (props.mode === '2d') {
      runtime.current?.setMode('2d');
      return;
    }
    const previous = builtFrom.current;
    if (
      runtime.current &&
      !runtime.current.isFailed() &&
      previous?.nodes === props.nodes &&
      previous?.edges === props.edges &&
      previous?.theme === props.theme &&
      previous?.focusZone === props.focusZone &&
      previous?.scopeKey === props.scopeKey
    ) {
      runtime.current.setMode(props.mode);
      return;
    }
    runtime.current?.dispose();
    runtime.current = null;
    try {
      runtime.current = mountScene(host.current, current, views.current);
      builtFrom.current = props;
    } catch {
      current.current.onError(
        '当前浏览器无法创建 3D 场景，已切换至 2D 平面视图。',
      );
    }
  }, [
    props.nodes,
    props.edges,
    props.theme,
    props.focusZone,
    props.scopeKey,
    props.mode,
  ]);
  useEffect(
    () => () => {
      runtime.current?.dispose();
      runtime.current = null;
    },
    [],
  );
  useEffect(() => {
    runtime.current?.highlight();
  }, [props.selectedId, props.highlightedIds]);
  useEffect(() => {
    if (props.resetKey) runtime.current?.fit();
  }, [props.resetKey]);
  return (
    <div
      ref={host}
      data-testid="topology-3d-scene"
      style={{ position: 'absolute', inset: 0, overflow: 'hidden' }}
    />
  );
}
