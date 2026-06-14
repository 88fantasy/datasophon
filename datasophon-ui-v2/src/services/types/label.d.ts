declare namespace DATASOPHON {
  /** @deprecated 使用 NodeLabelResponse 替代 */
  interface NodeLabel {
    id: number;
    nodeLabel: string;
    clusterId?: number;
  }

  /** 节点标签响应，对应后端 NodeLabelResponse */
  interface NodeLabelResponse {
    id: number;
    nodeLabel: string;
    clusterId?: number;
  }
}
