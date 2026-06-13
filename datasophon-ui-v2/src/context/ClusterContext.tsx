import React from 'react';

export interface ClusterContextValue {
  clusterId: number;
  clusterInfo: DATASOPHON.ClusterInfo;
}

const ClusterContext = React.createContext<ClusterContextValue | null>(null);

export default ClusterContext;
