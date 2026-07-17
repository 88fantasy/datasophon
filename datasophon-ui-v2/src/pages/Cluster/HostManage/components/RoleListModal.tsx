import { ModalForm } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Empty, Tag } from 'antd';
import React from 'react';
import { getHostRoles } from '@/services/host';

interface Props {
  clusterId: number;
  hostname: string;
  trigger: React.ReactElement;
}

const STATE_COLOR: Record<string, string> = {
  '1': 'success',
  '2': 'error',
};

const RoleListModal: React.FC<Props> = ({ clusterId, hostname, trigger }) => {
  const { data: roles = [], loading } = useRequest(
    () => getHostRoles(clusterId, hostname),
    {
      formatResult: (res) => res.data ?? [],
    },
  );

  return (
    <ModalForm
      title={`角色列表 - ${hostname}`}
      trigger={trigger}
      width={600}
      submitter={false}
      modalProps={{ destroyOnHidden: true, footer: false }}
    >
      {roles.length > 0 ? (
        <div>
          {roles.map((role: DATASOPHON.HostRoleResponse) => (
            <Tag
              style={{ marginBottom: 8, padding: '4px 8px' }}
              color={
                STATE_COLOR[String(role.serviceRoleStateCode)] ?? 'warning'
              }
              key={role.id}
            >
              {role.serviceRoleName}
            </Tag>
          ))}
        </div>
      ) : (
        !loading && <Empty description="暂无数据" />
      )}
    </ModalForm>
  );
};

export default RoleListModal;
