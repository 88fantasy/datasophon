declare namespace DATASOPHON {
  /** 节点标签，对应后端 ClusterNodeLabelEntity */
  interface NodeLabel {
    id: number;
    nodeLabel: string;
    clusterId?: number;
  }
}
