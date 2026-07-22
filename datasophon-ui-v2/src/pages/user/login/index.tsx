import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { FormattedMessage, SelectLang, useIntl, useModel } from '@umijs/max';
import { Alert } from 'antd';
import { createStyles } from 'antd-style';
import React, { startTransition, useState } from 'react';
import { login } from '@/services/auth';
import Logo from './Logo';

const useStyles = createStyles(({ token }) => ({
  lang: {
    width: 42,
    height: 42,
    lineHeight: '42px',
    position: 'fixed',
    right: 16,
    borderRadius: token.borderRadius,
    ':hover': {
      backgroundColor: token.colorBgTextHover,
    },
  },
  container: {
    display: 'flex',
    flexDirection: 'column',
    height: '100vh',
    overflow: 'auto',
    backgroundColor: token.colorBgLayout,
  },
}));

const Lang = () => {
  const { styles } = useStyles();
  return (
    <div className={styles.lang} data-lang>
      {SelectLang && <SelectLang />}
    </div>
  );
};

const LoginMessage: React.FC<{ content: string }> = ({ content }) => (
  <Alert style={{ marginBottom: 24 }} title={content} type="error" showIcon />
);

/**
 * Only allow same-origin relative paths; block open-redirect attacks.
 */
const getSafeRedirectUrl = (redirect: string | null): string => {
  if (!redirect?.startsWith('/')) return '/';
  if (redirect.startsWith('//')) return '/';
  try {
    const parsed = new URL(redirect, window.location.origin);
    if (parsed.origin !== window.location.origin) return '/';
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return '/';
  }
};

const Login: React.FC = () => {
  const [loginError, setLoginError] = useState(false);
  const { setInitialState } = useModel('@@initialState');
  const intl = useIntl();

  const handleSubmit = async (values: {
    username: string;
    password: string;
  }) => {
    setLoginError(false);
    try {
      const msg = await login(values);
      // msg is undefined when errorHandler handled a BizError (success: false)
      if (!msg?.data) {
        setLoginError(true);
        return;
      }
      startTransition(() => {
        setInitialState((s) => ({ ...s, currentUser: msg.data }));
      });
      const urlParams = new URL(window.location.href).searchParams;
      window.location.href = getSafeRedirectUrl(urlParams.get('redirect'));
    } catch {
      setLoginError(true);
    }
  };

  return (
    <div className="h-[100vh] flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100  overflow-hidden">
      <Logo />
      <div className="flex-1 flex  items-center justify-center h-full bg-white">
        <div className="w-[500px] bg-white p-8 sm:p-10 transition-all duration-300">
          <Lang />
          <div style={{ flex: '1', padding: '32px 0' }}>
            <LoginForm
              contentStyle={{ minWidth: 280, maxWidth: '75vw' }}
              logo={
                <img
                  alt="logo"
                  src={`${process.env.NODE_ENV === 'development' ? '/' : '/ddh/static/'}logo.svg`}
                />
              }
              title="DataSophon"
              subTitle={intl.formatMessage({
                id: 'pages.layouts.userLayout.title',
                defaultMessage: '大数据集群自动化部署与运维管理平台',
              })}
              onFinish={handleSubmit}
            >
              {loginError && (
                <LoginMessage
                  content={intl.formatMessage({
                    id: 'pages.login.accountLogin.errorMessage',
                    defaultMessage: '用户名或密码错误',
                  })}
                />
              )}

              <ProFormText
                name="username"
                fieldProps={{ size: 'large', prefix: <UserOutlined /> }}
                placeholder={intl.formatMessage({
                  id: 'pages.login.username.placeholder',
                  defaultMessage: '用户名',
                })}
                rules={[
                  {
                    required: true,
                    message: (
                      <FormattedMessage
                        id="pages.login.username.required"
                        defaultMessage="请输入用户名！"
                      />
                    ),
                  },
                ]}
              />

              <ProFormText.Password
                name="password"
                fieldProps={{ size: 'large', prefix: <LockOutlined /> }}
                placeholder={intl.formatMessage({
                  id: 'pages.login.password.placeholder',
                  defaultMessage: '密码',
                })}
                rules={[
                  {
                    required: true,
                    message: (
                      <FormattedMessage
                        id="pages.login.password.required"
                        defaultMessage="请输入密码！"
                      />
                    ),
                  },
                ]}
              />
            </LoginForm>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
