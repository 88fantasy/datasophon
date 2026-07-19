import { Modal, Spin } from 'antd';
import React, { useEffect, useState } from 'react';
import { getRoleInstanceLog } from '@/services/service';

interface Props {
  open: boolean;
  record: DATASOPHON.ServiceRoleInstanceInfo | null;
  clusterId: number;
  instanceId: number;
  onClose: () => void;
}

const LogModal: React.FC<Props> = ({
  open,
  record,
  clusterId,
  instanceId,
  onClose,
}) => {
  const [loading, setLoading] = useState(false);
  const [logText, setLogText] = useState('');

  useEffect(() => {
    if (!open || !record) return;
    let cancelled = false;
    setLogText('');
    setLoading(true);
    getRoleInstanceLog(clusterId, instanceId, record.id)
      .then((res) => {
        if (cancelled) return;
        const text = (res as any)?.data ?? '';
        setLogText(text);
      })
      .catch(() => {
        if (!cancelled) setLogText('获取日志失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, record, clusterId, instanceId]);

  return (
    <Modal
      title={`日志 — ${record?.serviceRoleName ?? ''} @ ${record?.hostname ?? ''}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={800}
      destroyOnHidden
    >
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : (
        <pre
          style={{
            maxHeight: 480,
            overflow: 'auto',
            background: '#1e1e1e',
            color: '#d4d4d4',
            padding: 16,
            borderRadius: 4,
            fontSize: 12,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}
        >
          {logText || '（暂无日志）'}
        </pre>
      )}
    </Modal>
  );
};

export default LogModal;
