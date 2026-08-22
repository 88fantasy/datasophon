import {
  ModalForm,
  ProFormDependency,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Alert, message } from 'antd';
import { createCluster, listFrames, updateCluster } from '@/services/cluster';

interface Props {
  trigger: React.ReactElement;
  cluster?: DATASOPHON.ClusterResponse;
  onSuccess: () => void;
}

const BuildOrEditModal: React.FC<Props> = ({ trigger, cluster, onSuccess }) => {
  // useRequest auto-unwraps { data: FrameResponse[] } → FrameResponse[]
  const { data: frames } = useRequest(listFrames);

  const frameOptions = (frames ?? []).map((f: DATASOPHON.FrameResponse) => ({
    label: `${f.frameCode}`,
    value: f.id,
  }));

  const archOptions = [
    { label: '物理集群', value: 'physical' },
    { label: 'K8s 集群', value: 'k8s' },
  ];

  const handleFinish = async (values: DATASOPHON.CreateClusterRequest) => {
    try {
      if (cluster?.id) {
        // UpdateClusterRequest 只包含 clusterName 和 clusterCode
        await updateCluster(cluster.id, {
          clusterName: values.clusterName,
          clusterCode: values.clusterCode,
        });
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
              manageMode: cluster.manageMode ?? 'MANAGED',
            }
          : { archType: 'physical', manageMode: 'MANAGED' }
      }
      onFinish={handleFinish}
      modalProps={{ destroyOnHidden: true }}
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
      {/* 接管模式只对 K8s 集群有意义：物理集群由平台逐节点安装，没有「已存在的集群」可接管 */}
      <ProFormDependency name={['archType']}>
        {({ archType }) =>
          archType === 'k8s' ? (
            <>
              <ProFormRadio.Group
                name="manageMode"
                label="创建方式"
                radioType="button"
                options={[
                  { label: '新建集群', value: 'MANAGED' },
                  { label: '接管现有集群', value: 'IMPORTED' },
                ]}
                rules={[{ required: true, message: '请选择创建方式' }]}
              />
              <ProFormDependency name={['manageMode']}>
                {({ manageMode }) =>
                  manageMode === 'IMPORTED' ? (
                    <Alert
                      type="info"
                      showIcon
                      style={{ marginBottom: 24 }}
                      message="接管模式为只读监控"
                      description="创建后需配置集群连接与 Doris 数据源，再扫描并登记已存在的服务。平台不会向该集群下发任何变更。"
                    />
                  ) : null
                }
              </ProFormDependency>
            </>
          ) : null
        }
      </ProFormDependency>
    </ModalForm>
  );
};

export default BuildOrEditModal;
