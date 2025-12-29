import React, { useCallback, useEffect, useState } from 'react';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Checkbox, Form, Input, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { API } from '../../api';
import { axiosPost } from '../../api/request';
import { setAuthorization } from '../../utils/request';
import { account } from '../../utils/account';
import { VUE_APP_PUBLIC_PATH } from '../../config';
import { showMsgAfferRequest } from '../../utils/util';

interface LoginFormValues {
  username: string;
  password: string;
  remember: boolean;
}

const { Title, Text } = Typography;

const Login = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    // 模拟登录请求
    // setTimeout(() => {
    // 保存用户信息到 localStorage
    // localStorage.setItem('user', JSON.stringify({
    //   username: values.username,
    //   isLoggedIn: true
    // }));
    const res = await axiosPost(
      API.login,
      {
        username: values.username,
        password: values.password
      }
    )

    console.log('res', res)
    if (res.code === 200) {
      showMsgAfferRequest(res)

      const loginRes = res.data;
      setAuthorization({ sessionId: loginRes.sessionId });
      account.setUser(res.userInfo);
      navigate(`${VUE_APP_PUBLIC_PATH}/Colony/ColonyManage`)
    }
    setLoading(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 p-4">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-2xl shadow-xl p-8 sm:p-10 transition-all duration-300 hover:shadow-2xl">
          <div className="text-center mb-8">
            <Title level={3} className="!mb-2">VOS登录</Title>
            <Text type="secondary">请输入您的账户信息</Text>
          </div>

          <Form
            name="login_form"
            initialValues={{ remember: true }}
            onFinish={onFinish}
            autoComplete="off"
            layout="vertical"
          >
            <Form.Item
              label="用户名"
              name="username"
              rules={[{ required: true, message: '请输入用户名!' }]}
            >
              <Input
                prefix={<UserOutlined className="text-gray-400" />}
                placeholder="请输入用户名"
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="密码"
              name="password"
              rules={[{ required: true, message: '请输入密码!' }]}
            >
              <Input.Password
                prefix={<LockOutlined className="text-gray-400" />}
                placeholder="请输入密码"
                size="large"
              />
            </Form.Item>

            {/* <Form.Item name="remember" valuePropName="checked">
              <Checkbox>记住我</Checkbox>
              <a className="float-right text-blue-500 hover:text-blue-700" href="#">
                忘记密码?
              </a>
            </Form.Item> */}

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                size="large"
                loading={loading}
                className="w-full font-medium"
              >
                登录
              </Button>
            </Form.Item>
          </Form>

          <div className="mt-8 text-center">
            <Text type="secondary" className="text-sm">
              © {new Date().getFullYear()} 管理系统. 保留所有权利
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;