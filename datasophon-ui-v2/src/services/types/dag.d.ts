declare namespace DATASOPHON {
  /** DAG 状态五态，与后端 DagStatus 枚举对应 */
  type DagStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCEL';

  /** 命令历史列表条目，对应后端 DagDefinitionEntity */
  interface DagCommand {
    id: string;
    clusterId: number;
    dagName: string;
    description?: string;
    status: DagStatus;
    createdTime?: string;
    startedTime?: string;
    completedTime?: string;
  }

  /** DAG 节点，对应后端 InstallProgressDAG.nodes 中单个节点 */
  interface DagNode {
    id: string | number;
    serviceInstanceId?: number;
    clusterId?: number;
    archType?: string;
    status: DagStatus;
    /** 节点显示名称 */
    name?: string;
    /** 主机名 */
    hostname?: string;
    /** 进度 0-100 */
    progress?: number;
    /** 命令 ID，用于查看日志 */
    commandId?: string | number;
    [key: string]: any;
  }

  /** DAG 边 */
  interface DagEdge {
    id: string | number;
    start: string | number;
    end: string | number;
  }

  /** DAG 图完整数据，对应后端 InstallProgressDAG */
  interface DagGraph {
    clusterId: number;
    archType?: string;
    nodes: DagNode[];
    edges: DagEdge[];
  }

  /** 命令历史条目响应体，对应后端 DagCommandResponse */
  interface DagCommandResponse {
    id: string;
    clusterId: number;
    dagName: string;
    description?: string;
    status: DagStatus;
    createdTime?: string;
    startedTime?: string;
    completedTime?: string;
  }

  /** 命令历史分页响应体，对应后端 DagCommandPageResponse */
  interface DagCommandPageResponse {
    records: DagCommandResponse[];
    total: number;
  }
}
