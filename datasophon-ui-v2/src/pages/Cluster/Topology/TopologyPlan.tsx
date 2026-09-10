import { select, zoom, zoomIdentity } from 'd3';
import { useEffect, useMemo, useRef } from 'react';
import { serviceIconFor } from '../ObservabilityCollector/serviceIcon';
import { layoutTopology } from './topologyLayout';
import { TOPOLOGY_ZONES, type TopologySceneProps } from './types';

const STATE_COLORS = {
  running: '#20a780',
  warning: '#db9625',
  stopped: '#df6472',
  unknown: '#95a1b3',
};

/** 平面模式使用 SVG，WebGL 不可用时仍可浏览同一份部署数据。 */
export default function TopologyPlan(props: TopologySceneProps) {
  const {
    nodes,
    edges,
    focusZone,
    selectedId,
    highlightedIds,
    theme,
    resetKey,
    scopeKey,
    onSelect,
    onEnterZone,
  } = props;
  const svgRef = useRef<SVGSVGElement>(null);
  const groupRef = useRef<SVGGElement>(null);
  const layout = useMemo(
    () => layoutTopology(nodes, focusZone),
    [nodes, focusZone],
  );
  const positions = useMemo(
    () => new Map(layout.nodes.map((item) => [item.node.id, item])),
    [layout],
  );
  const dark = theme === 'dark';
  const minX =
    Math.min(0, ...layout.zones.map((item) => item.x - item.width / 2)) - 1;
  const minZ =
    Math.min(0, ...layout.zones.map((item) => item.z - item.depth / 2)) - 1;
  const maxX =
    Math.max(1, ...layout.zones.map((item) => item.x + item.width / 2)) + 1;
  const maxZ =
    Math.max(1, ...layout.zones.map((item) => item.z + item.depth / 2)) + 1;

  useEffect(() => {
    if (!svgRef.current || !groupRef.current) return;
    const svg = select(svgRef.current);
    const layer = select(groupRef.current);
    const behavior = zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.3, 8])
      .on('zoom', (event) =>
        layer.attr('transform', event.transform.toString()),
      );
    svg.call(behavior).on('dblclick.zoom', null);
    svg.call(behavior.transform, zoomIdentity);
    return () => {
      svg.on('.zoom', null);
    };
  }, [focusZone, scopeKey, resetKey]);

  return (
    <svg
      ref={svgRef}
      width="100%"
      height="100%"
      viewBox={`${minX} ${minZ} ${maxX - minX} ${maxZ - minZ}`}
      role="img"
      aria-label="集群平面部署拓扑，可拖拽平移和滚轮缩放"
      style={{ touchAction: 'none' }}
    >
      <title>集群平面部署拓扑</title>
      <defs>
        <marker
          id="topology-plan-arrow"
          markerWidth="6"
          markerHeight="6"
          refX="5"
          refY="3"
          orient="auto"
          markerUnits="strokeWidth"
        >
          <path d="M0,0 L6,3 L0,6 Z" fill={dark ? '#7696bc' : '#91a9c6'} />
        </marker>
      </defs>
      <g ref={groupRef}>
        {layout.zones.map(({ zone, x, z, width, depth }) => (
          <g key={zone.id}>
            <rect
              x={x - width / 2}
              y={z - depth / 2}
              width={width}
              height={depth}
              rx="0.45"
              fill={dark ? '#14243a' : '#f5f8fc'}
              stroke={zone.color}
              strokeOpacity="0.4"
              strokeWidth="0.025"
            />
            <text
              x={x - width / 2 + 0.4}
              y={z - depth / 2 + 0.65}
              fontSize="0.36"
              fontWeight="600"
              fill={dark ? '#d4e1f6' : '#31445f'}
            >
              {zone.label}
            </text>
            {!nodes.some((node) => node.zone === zone.id) && (
              <text
                x={x}
                y={z}
                textAnchor="middle"
                fontSize="0.26"
                fill="#8b9bb0"
              >
                暂无登记资源
              </text>
            )}
            {/* biome-ignore lint/a11y/useSemanticElements: SVG 热区需要 SVG 元素，已提供键盘操作和按钮语义。 */}
            <rect
              x={x - width / 2}
              y={z - depth / 2}
              width={width}
              height="1.1"
              fill="transparent"
              role="button"
              tabIndex={0}
              aria-label={`进入${zone.label}分区`}
              onClick={() => onEnterZone(zone.id)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onEnterZone(zone.id);
                }
              }}
            />
          </g>
        ))}
        {edges.map((edge) => {
          const source = positions.get(edge.source);
          const target = positions.get(edge.target);
          if (!source || !target) return null;
          return (
            <path
              key={edge.id}
              d={`M${source.x},${source.z} L${target.x},${target.z}`}
              fill="none"
              stroke={dark ? '#7696bc' : '#91a9c6'}
              strokeWidth="0.03"
              strokeDasharray={
                edge.kind === 'schematic' ? '0.15 0.12' : undefined
              }
              markerEnd="url(#topology-plan-arrow)"
            >
              <title>{edge.label}</title>
            </path>
          );
        })}
        {layout.nodes.map(({ node, x, z }) => {
          const color = TOPOLOGY_ZONES.find(
            (zone) => zone.id === node.zone,
          )?.color;
          const active =
            selectedId === node.id || highlightedIds?.includes(node.id);
          const dim =
            highlightedIds &&
            !highlightedIds.includes(node.id) &&
            selectedId !== node.id;
          const symbol =
            node.kind === 'host' ? '▤' : node.kind === 'browser' ? '▣' : '◇';
          return (
            // biome-ignore lint/a11y/useSemanticElements: SVG 节点无法使用 HTML button，已提供键盘操作和按钮语义。
            <g
              key={node.id}
              transform={`translate(${x},${z})`}
              role="button"
              tabIndex={0}
              aria-label={`查看${node.label}`}
              opacity={dim ? 0.35 : 1}
              style={{ cursor: 'pointer' }}
              onClick={() => onSelect(node.id)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onSelect(node.id);
                }
              }}
            >
              <rect
                x="-1.4"
                y="-0.95"
                width="2.8"
                height="1.9"
                rx="0.17"
                fill={dark ? '#1d3049' : '#fff'}
                stroke={active ? color : dark ? '#344b66' : '#d4deea'}
                strokeWidth={active ? '0.065' : '0.025'}
              />
              <rect
                x="-1.39"
                y="-0.94"
                width="0.055"
                height="1.88"
                rx="0.03"
                fill={color}
              />
              {node.serviceName ? (
                <image
                  href={serviceIconFor(node.serviceName).src}
                  x="-0.25"
                  y="-0.74"
                  width="0.5"
                  height="0.5"
                />
              ) : (
                <text
                  x="0"
                  y="-0.26"
                  textAnchor="middle"
                  fontSize="0.56"
                  fill={color}
                >
                  {symbol}
                </text>
              )}
              <circle
                cx="1.12"
                cy="-0.67"
                r="0.055"
                fill={STATE_COLORS[node.state]}
              />
              <text
                x="0"
                y="0.16"
                textAnchor="middle"
                fontSize="0.25"
                fontWeight="600"
                fill={dark ? '#e4ecf8' : '#263a55'}
              >
                {node.label.length > 18
                  ? `${node.label.slice(0, 17)}…`
                  : node.label}
              </text>
              <text
                x="0"
                y="0.51"
                textAnchor="middle"
                fontSize="0.19"
                fill={dark ? '#9caec5' : '#748399'}
              >
                {node.subtitle.length > 23
                  ? `${node.subtitle.slice(0, 22)}…`
                  : node.subtitle}
              </text>
              <title>
                {node.label} · {node.subtitle}
              </title>
            </g>
          );
        })}
      </g>
    </svg>
  );
}
