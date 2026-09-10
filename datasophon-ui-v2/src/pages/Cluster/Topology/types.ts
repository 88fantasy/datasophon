export type TopologyMode = '3d' | '2.5d' | '2d';
export type TopologyTheme = 'light' | 'dark';
export type ZoneId = 'edge' | 'k8s' | 'doris' | 'vm' | 'other';
export type ResourceState = 'running' | 'warning' | 'stopped' | 'unknown';

export interface TopologyInstance {
  id: string;
  label: string;
  hostname?: string;
  state: ResourceState;
  ports?: string;
}

export interface TopologyNode {
  id: string;
  label: string;
  subtitle: string;
  zone: ZoneId;
  kind: 'cluster' | 'host' | 'service' | 'browser' | 'gateway';
  state: ResourceState;
  source: 'inventory' | 'schematic';
  serviceName?: string;
  /** 服务器身份与其内部服务的父级关系。 */
  parentId?: string;
  resourceId?: string;
  address?: string;
  details: { label: string; value: string }[];
  instances: TopologyInstance[];
  href?: string;
}

export interface TopologyEdge {
  id: string;
  source: string;
  target: string;
  kind: 'deployment' | 'schematic';
  label: string;
}

export interface TopologyZone {
  id: ZoneId;
  label: string;
  subtitle: string;
  color: string;
}

export const TOPOLOGY_ZONES: TopologyZone[] = [
  { id: 'edge', label: '前置区', subtitle: '访问入口', color: '#27a68a' },
  {
    id: 'k8s',
    label: 'Kubernetes集群',
    subtitle: '计算与应用服务',
    color: '#4385f5',
  },
  { id: 'doris', label: 'Doris集群', subtitle: '分析数据库', color: '#24b7c5' },
  {
    id: 'vm',
    label: 'VM集群',
    subtitle: '独立部署与基础中间件',
    color: '#da9850',
  },
  {
    id: 'other',
    label: '其他',
    subtitle: '未配置类别或自定义标签',
    color: '#8b7bdb',
  },
];

export interface TopologySnapshot {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
  warnings: string[];
  observedAt: string;
}

export interface TopologySceneProps {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
  mode: TopologyMode;
  theme: TopologyTheme;
  focusZone: ZoneId | null;
  selectedId: string | null;
  /** 搜索结果仅高亮，不删除拓扑节点或改变部署关系。 */
  highlightedIds: string[] | null;
  resetKey: number;
  scopeKey?: string;
  onSelect: (id: string | null) => void;
  onEnterZone: (id: ZoneId) => void;
  onError: (message: string) => void;
}
