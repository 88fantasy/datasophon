import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { createUser, updateUser } from '@/services/datasophon/user';

interface Props {
  trigger: React.ReactElement;
  user?: DATASOPHON.UserInfo;
  onSuccess: () => void;
}

const UserModal: React.FC<Props> = ({ trigger, user, onSuccess }) => {
  const isEdit = !!user?.id;

  const handleFinish = async (values: {
    username: string;
    password?: string;
    email?: string;
    phone?: string;
  }) => {
    try {
      if (isEdit) {
        await updateUser(user.id, {
          username: values.username,
          email: values.email,
          phone: values.phone,
        });
        message.success('用户更新成功');
      } else {
        await createUser(values);
        message.success('用户创建成功');
      }
      onSuccess();
      return true;
    } catch {
      return false;
    }
  };

  return (
    <ModalForm
      title={isEdit ? '编辑用户' : '新建用户'}
      trigger={trigger}
      initialValues={
        isEdit
          ? {
              username: user.username,
              email: user.email,
              phone: user.phone,
            }
          : {}
      }
      onFinish={handleFinish}
      modalProps={{ destroyOnClose: true }}
      width={480}
    >
      <ProFormText
        name="username"
        label="用户名"
        placeholder="请输入用户名"
        rules={[{ required: true, message: '请输入用户名' }]}
      />
      {!isEdit && (
        <ProFormText.Password
          name="password"
          label="密码"
          placeholder="请输入密码"
          rules={[{ required: true, message: '请输入密码' }]}
        />
      )}
      <ProFormText
        name="email"
        label="邮箱"
        placeholder="请输入邮箱"
        rules={[{ required: true, message: '请输入邮箱' }]}
      />
      <ProFormText
        name="phone"
        label="电话"
        placeholder="请输入电话"
        rules={[{ required: true, message: '请输入电话' }]}
      />
    </ModalForm>
  );
};

export default UserModal;
