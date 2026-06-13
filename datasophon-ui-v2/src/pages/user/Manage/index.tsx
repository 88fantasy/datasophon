import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, message, Popconfirm } from 'antd';
import React, { useRef } from 'react';
import { deleteUsers, listUsers } from '@/services/datasophon/user';
import ResetPasswordModal from './ResetPasswordModal';
import UserModal from './UserModal';

const UserManage: React.FC = () => {
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumns<DATASOPHON.UserInfo>[] = [
    {
      dataIndex: 'index',
      title: '序号',
      valueType: 'indexBorder',
      width: 48,
      search: false,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      ellipsis: true,
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      ellipsis: true,
      search: false,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      search: false,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      search: false,
      render: (_, record) => [
        <UserModal
          key="edit"
          user={record}
          onSuccess={() => actionRef.current?.reload()}
          trigger={
            <Button type="link" size="small">
              编辑
            </Button>
          }
        />,
        <ResetPasswordModal
          key="password"
          user={record}
          trigger={
            <Button type="link" size="small">
              重置密码
            </Button>
          }
        />,
        <Popconfirm
          key="delete"
          title="确认删除该用户？"
          disabled={record.userType === 1}
          onConfirm={async () => {
            await deleteUsers([record.id]);
            message.success('删除成功');
            actionRef.current?.reload();
          }}
        >
          <Button
            type="link"
            size="small"
            danger
            disabled={record.userType === 1}
          >
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer>
      <ProTable<DATASOPHON.UserInfo>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="用户列表"
        search={{ filterType: 'light' }}
        request={async (params) => {
          const { current, pageSize, username } = params;
          const result = await listUsers({
            page: current ?? 1,
            pageSize: pageSize ?? 20,
            username,
          });
          return {
            data: result.data.records ?? [],
            total: result.data.total ?? 0,
            success: true,
          };
        }}
        columns={columns}
        pagination={{ pageSize: 20 }}
        toolBarRender={() => [
          <UserModal
            key="create"
            onSuccess={() => actionRef.current?.reload()}
            trigger={<Button type="primary">新建用户</Button>}
          />,
        ]}
      />
    </PageContainer>
  );
};

export default UserManage;
