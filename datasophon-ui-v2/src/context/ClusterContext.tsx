import React from 'react';

export interface ClusterContextValue {
  clusterId: number;
  clusterInfo: DATASOPHON.ClusterInfo;
  /** 物理集群侧栏轮询的服务快照；独立弹窗不提供。 */
  serviceList?: DATASOPHON.ServiceInstanceInfo[];
}

const ClusterContext = React.createContext<ClusterContextValue | null>(null);

export default ClusterContext;
