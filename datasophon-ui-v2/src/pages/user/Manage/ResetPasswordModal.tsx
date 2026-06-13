import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { resetPassword } from '@/services/user';

interface Props {
  trigger: React.ReactElement;
  user: DATASOPHON.UserInfo;
}

const ResetPasswordModal: React.FC<Props> = ({ trigger, user }) => {
  const handleFinish = async (values: {
    password: string;
    confirm: string;
  }) => {
    try {
      await resetPassword(user.id, values.password);
      message.success('密码重置成功');
      return true;
    } catch {
      return false;
    }
  };

  return (
    <ModalForm
      title={`重置密码 — ${user.username}`}
      trigger={trigger}
      onFinish={handleFinish}
      modalProps={{ destroyOnClose: true }}
      width={400}
    >
      <ProFormText.Password
        name="password"
        label="新密码"
        placeholder="请输入新密码"
        rules={[{ required: true, message: '请输入新密码' }]}
      />
      <ProFormText.Password
        name="confirm"
        label="确认密码"
        placeholder="请再次输入新密码"
        dependencies={['password']}
        rules={[
          { required: true, message: '请再次输入新密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (!value || getFieldValue('password') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('两次输入的密码不一致'));
            },
          }),
        ]}
      />
    </ModalForm>
  );
};

export default ResetPasswordModal;
