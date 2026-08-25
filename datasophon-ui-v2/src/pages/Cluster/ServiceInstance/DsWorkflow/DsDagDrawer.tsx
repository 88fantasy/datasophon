import { useIntl } from '@umijs/max';
import { Alert, Drawer, Spin } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import DsDagGraph from './DsDagGraph';
import { getDsDag } from './service';

interface DsDagDrawerProps {
  clusterId: number;
  projectCode: number;
  instance?: DATASOPHON.DsWorkflowInstance;
  open: boolean;
  onClose: () => void;
}

const DsDagDrawer: React.FC<DsDagDrawerProps> = ({
  clusterId,
  projectCode,
  instance,
  open,
  onClose,
}) => {
  const intl = useIntl();
  const [dag, setDag] = useState<DATASOPHON.DsDag>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const loadDag = useCallback(async () => {
    if (!open || !instance) return;
    setLoading(true);
    try {
      const response = await getDsDag(clusterId, projectCode, instance.id);
      setDag(response.data);
      setError(false);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [clusterId, instance, open, projectCode]);

  useEffect(() => {
    if (!open || !instance) {
      setDag(undefined);
      setError(false);
      return;
    }
    void loadDag();
    const timer = window.setInterval(() => void loadDag(), 15_000);
    return () => window.clearInterval(timer);
  }, [instance, loadDag, open]);

  return (
    <Drawer
      open={open}
      size="90%"
      destroyOnHidden
      title={
        instance?.name ?? intl.formatMessage({ id: 'dsWorkflow.dag.title' })
      }
      onClose={onClose}
    >
      {error ? (
        <Alert
          showIcon
          type="error"
          title={intl.formatMessage({ id: 'dsWorkflow.error.unavailable' })}
        />
      ) : null}
      {loading && !dag ? <Spin /> : null}
      {dag ? <DsDagGraph dag={dag} /> : null}
    </Drawer>
  );
};

export default DsDagDrawer;
