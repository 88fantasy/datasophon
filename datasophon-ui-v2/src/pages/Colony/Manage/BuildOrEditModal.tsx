import {
  ModalForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { message } from 'antd';
import {
  createCluster,
  listFrames,
  updateCluster,
} from '@/services/cluster';

interface Props {
  trigger: React.ReactElement;
  cluster?: DATASOPHON.ClusterInfo;
  onSuccess: () => void;
}

const BuildOrEditModal: React.FC<Props> = ({ trigger, cluster, onSuccess }) => {
  // useRequest auto-unwraps { data: FrameInfo[] } → FrameInfo[]
  const { data: frames } = useRequest(listFrames);

  const frameOptions = (frames ?? []).map((f: DATASOPHON.FrameInfo) => ({
    label: `${f.frameCode}`,
    value: f.id,
  }));

  const archOptions = [
    { label: '物理集群', value: 'physical' },
    { label: 'K8s 集群', value: 'k8s' },
  ];

  const handleFinish = async (values: {
    clusterName: string;
    clusterCode: string;
    frameId: number;
    archType: DATASOPHON.ClusterArchType;
  }) => {
    try {
      if (cluster?.id) {
        await updateCluster(cluster.id, values);
        message.success('集群更新成功');
      } else {
        await createCluster(values);
        message.success('集群创建成功');
      }
      onSuccess();
      return true;
    } catch {
      return false;
    }
  };

  return (
    <ModalForm
      title={cluster?.id ? '编辑集群' : '新建集群'}
      trigger={trigger}
      initialValues={
        cluster
          ? {
              clusterName: cluster.clusterName,
              clusterCode: cluster.clusterCode,
              frameId: cluster.frameId,
              archType: cluster.archType,
            }
          : { archType: 'physical' }
      }
      onFinish={handleFinish}
      modalProps={{ destroyOnClose: true }}
      width={480}
    >
      <ProFormText
        name="clusterName"
        label="集群名称"
        placeholder="请输入集群名称"
        rules={[{ required: true, message: '请输入集群名称' }]}
      />
      <ProFormText
        name="clusterCode"
        label="集群代号"
        placeholder="英文字母或下划线，如 prod_hadoop"
        rules={[{ required: true, message: '请输入集群代号' }]}
      />
      <ProFormSelect
        name="frameId"
        label="框架版本"
        options={frameOptions}
        placeholder="请选择框架版本"
        rules={[{ required: true, message: '请选择框架版本' }]}
      />
      <ProFormSelect
        name="archType"
        label="集群类型"
        options={archOptions}
        rules={[{ required: true, message: '请选择集群类型' }]}
      />
    </ModalForm>
  );
};

export default BuildOrEditModal;
