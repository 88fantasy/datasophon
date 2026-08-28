import { history, useIntl, useParams } from '@umijs/max';
import { Alert, Button, Space, Spin } from 'antd';
import { useCallback, useContext, useEffect, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import DsDagGraph from './DsDagGraph';
import { classifyDsError, type DsErrorKind } from './errors';
import { getDsDag } from './service';
import styles from './DsWorkflow.module.less';

const DsDagPage: React.FC = () => {
  const intl = useIntl();
  const clusterId = useContext(ClusterContext)?.clusterId;
  const { instanceId, projectCode, workflowInstanceId } = useParams<{
    instanceId: string;
    projectCode: string;
    workflowInstanceId: string;
  }>();
  const numericProjectCode = Number(projectCode);
  const numericWorkflowInstanceId = Number(workflowInstanceId);

  const [dag, setDag] = useState<DATASOPHON.DsDag>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<DsErrorKind>();

  const loadDag = useCallback(async () => {
    if (!clusterId) return;
    setLoading(true);
    try {
      const response = await getDsDag(
        clusterId,
        numericProjectCode,
        numericWorkflowInstanceId,
      );
      setDag(response.data);
      setError(undefined);
    } catch (requestError) {
      setError(classifyDsError(requestError));
    } finally {
      setLoading(false);
    }
  }, [clusterId, numericProjectCode, numericWorkflowInstanceId]);

  useEffect(() => {
    void loadDag();
    const timer = window.setInterval(() => void loadDag(), 15_000);
    return () => window.clearInterval(timer);
  }, [loadDag]);

  return (
    <div className={styles.dagPage}>
      <Space wrap className={styles.dagHeader}>
        <Button
          onClick={() =>
            history.push(`/cluster/${clusterId}/service/${instanceId}`)
          }
        >
          {intl.formatMessage({ id: 'dsWorkflow.dag.backToList' })}
        </Button>
        <span>
          {dag?.instance.name ??
            intl.formatMessage({ id: 'dsWorkflow.dag.title' })}
        </span>
      </Space>
      {error ? (
        <Alert
          showIcon
          type="error"
          title={intl.formatMessage({ id: `dsWorkflow.error.${error}` })}
          action={
            <Button size="small" onClick={() => void loadDag()}>
              {intl.formatMessage({ id: 'dsWorkflow.retry' })}
            </Button>
          }
        />
      ) : null}
      {loading && !dag ? <Spin /> : null}
      {dag ? <DsDagGraph dag={dag} /> : null}
    </div>
  );
};

export default DsDagPage;
