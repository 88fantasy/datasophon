import React, { useMemo } from 'react';
import ClusterContext from '@/context/ClusterContext';
import UploadManifestModal from '@/pages/Cluster/Deploy/UploadManifestModal';

interface Props {
  cluster: DATASOPHON.ClusterResponse;
  open: boolean;
  onClose: () => void;
}

const ImportManifestModal: React.FC<Props> = ({ cluster, open, onClose }) => {
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
      <UploadManifestModal open={open} onClose={onClose} />
    </ClusterContext.Provider>
  );
};

export default ImportManifestModal;
