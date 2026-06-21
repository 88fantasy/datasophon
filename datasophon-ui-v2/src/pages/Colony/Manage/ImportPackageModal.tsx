import React, { useMemo } from 'react';
import ClusterContext from '@/context/ClusterContext';
import UploadPackageModal from '@/pages/Cluster/Deploy/UploadPackageModal';

interface Props {
  cluster: DATASOPHON.ClusterResponse;
  open: boolean;
  onClose: () => void;
}

const ImportPackageModal: React.FC<Props> = ({ cluster, open, onClose }) => {
  const ctxValue = useMemo(
    () => ({
      clusterId: cluster.id,
      clusterInfo: {
        ...cluster,
        clusterFrame: cluster.clusterFrame ?? '',
      } as DATASOPHON.ClusterInfo,
    }),
    [cluster],
  );

  return (
    <ClusterContext.Provider value={ctxValue}>
      <UploadPackageModal open={open} onClose={onClose} />
    </ClusterContext.Provider>
  );
};

export default ImportPackageModal;
