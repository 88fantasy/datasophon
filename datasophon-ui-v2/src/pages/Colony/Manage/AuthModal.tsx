import { ModalForm, ProFormSelect } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import { listAllUsers, saveManagers } from '@/services/cluster';

interface Props {
  trigger: React.ReactElement;
  cluster: DATASOPHON.ClusterResponse;
  onSuccess: () => void;
}

const AuthModal: React.FC<Props> = ({ trigger, cluster, onSuccess }) => {
  // useRequest auto-unwraps { data: UserResponse[] } → UserResponse[]
  const { data: users } = useRequest(listAllUsers);

  const userOptions = (users ?? []).map((u: DATASOPHON.UserResponse) => ({
    label: u.username,
    value: u.id,
  }));

  const currentManagerIds = (cluster.clusterManagerList ?? []).map((m) => m.id);

  const handleFinish = async (values: { userIds: number[] }) => {
    try {
      await saveManagers(cluster.id, values.userIds ?? []);
      message.success('授权保存成功');
      onSuccess();
      return true;
    } catch {
      return false;
    }
  };

  return (
    <ModalForm
      title={`授权管理员 — ${cluster.clusterName}`}
      trigger={trigger}
      initialValues={{ userIds: currentManagerIds }}
      onFinish={handleFinish}
      modalProps={{ destroyOnHidden: true }}
      width={480}
    >
      <ProFormSelect
        name="userIds"
        label="管理员"
        mode="multiple"
        options={userOptions}
        placeholder="请选择管理员用户（可多选）"
      />
    </ModalForm>
  );
};

export default AuthModal;
