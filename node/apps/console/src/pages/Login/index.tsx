import { Alert, Button, message, Skeleton, Space, theme } from 'antd';
import React, { ReactNode, useEffect, useState } from 'react';
import styles from './index.module.css';
import { LoginForm, ProFormText } from '@ant-design/pro-components';
import {
  LockOutlined,
  SecurityScanOutlined,
  UserOutlined,
} from '@ant-design/icons';
import logo from '../../assets/logo.svg';
import {
  authenticatePasskey,
  globalNavigate,
  passkeyAvailable,
  useAuthenticatePasskeyMutation,
  useAuthenticationWorkStatusQuery,
} from '@private-everest/app-support';

const LoginMessage = ({ content }: { content: ReactNode }) => (
  <Alert style={{ marginBottom: 24 }} message={content} type="error" showIcon />
);
const useToken = theme.useToken

const onSuccessLogin = () => {
  message.success('登录成功！');
  // 处理登录后业务
  // setUserLoginState(msg);
  globalNavigate('/')
}

// /login 作为一个登录入口，同时应该也是一个支持
const Login = () => {
  console.info("render Login")
  const {data: passkeyWork} = useAuthenticationWorkStatusQuery(undefined)
  // 当前执行登录的状态
  const [working, setWorking] = useState(false)
  // noinspection JSUnusedLocalSymbols
  // @ts-ignore
  const [failed, setFailed] = useState(false)
  // const [userLoginState, setUserLoginState] = useState({});
  // noinspection JSUnusedLocalSymbols
  // @ts-ignore
  const [type, setType] = useState('account');
  // const { initialState, setInitialState } = useModel('@@initialState');
  const {token: {colorBgLayout, colorPrimary}} = useToken()
  // const [loginAction] = useLoginMutation()
  const loginAction: (any) | undefined = undefined
  const [authenticatePasskeyAction] = useAuthenticatePasskeyMutation()

  console.warn("跳转到登录目录1")
  // 目前阶段我们只提供了一种登录方式
  useEffect(() => {
    console.warn("跳转到登录目录")
    location.href = '/loginFeishu';
  }, [])


  if (passkeyWork == undefined) {
    return <Skeleton/>
  }

  // noinspection PointlessBooleanExpressionJS
  const handleSubmit = loginAction && (async (values: any) => {
    try {
      await loginAction({
        body: values,
        exceptionHandlers: {
          UsernameNotFoundException: async () => {
            message.error('该用户不存在');
          },
          BadCredentialsException: async () => {
            message.error('用户名或者密码错误');
          }
        }
      }).unwrap()
      // 登录逻辑
      // const msg = await login({...values, type});
      // if (msg.status === 'ok') {
      //   message.success('登录成功！');
      //   // await setInitialState((s) => ({ ...s, currentUser: msg.user }));
      //   // history.push('/');
      //   return;
      // }
      onSuccessLogin()
    } catch (error) {
      // message.error('登录失败，请重试！');
    }
  });

  return (
    <div style={{'--colorBgLayout': colorBgLayout, '--colorPrimary': colorPrimary} as React.CSSProperties}
         className={styles.container}>
      <div className={styles.content}>
        <LoginForm
          logo={<img alt="logo" src={logo}/>}
          title="超管后台"
          subTitle={<Space direction={"vertical"}>
            私密网站非请勿入
            {(passkeyAvailable && passkeyWork) &&
              <Button icon={<SecurityScanOutlined/>} size={"large"} onClick={async () => {
                const result = await authenticatePasskey()
                console.debug('拿着再配合 X-PasswordlessId-Authenticate 去 /passkeyLoginProcessing 登录', result)
                // 完事执行登录后
                await authenticatePasskeyAction({
                  body: result,
                  exceptionHandlers: {
                    BadCredentialsException: async () => {
                      message.error('Passkey 无法被识别');
                    }
                  }
                }).unwrap()
                onSuccessLogin()
              }}>免密登录</Button>}
          </Space>}
          // subTitle="私密网站非请勿入"
          initialValues={{autoLogin: true}}
          loading={working}
          onFinish={async (values) => {
            setWorking(true)
            try {
              await handleSubmit(values);
            } finally {
              setWorking(false)
            }
          }}
        >
          {/*<Tabs activeKey={type} onChange={setType}>*/}
          {/*  <Tabs.TabPane key="account" tab="账户密码登录"/>*/}
          {/*  <Tabs.TabPane key="mobile" tab="手机号登录"/>*/}
          {/*</Tabs>*/}

          {failed && type === 'account' && (
            <LoginMessage content="账户或密码错误"/>
          )}

          {type === 'account' && (
            <>
              <ProFormText
                name="username"
                fieldProps={{
                  size: 'large',
                  prefix: <UserOutlined className={styles.icon}/>,
                }}
                placeholder="用户名"
                rules={[
                  {
                    required: true,
                    message: '请输入用户名!',
                  },
                ]}
              />
              <ProFormText.Password
                name="password"
                fieldProps={{
                  size: 'large',
                  prefix: <LockOutlined className={styles.icon}/>,
                }}
                placeholder="密码"
                rules={[
                  {
                    required: true,
                    message: '请输入密码！',
                  },
                ]}
              />
            </>
          )}

          {/*<div style={{marginBottom: 24}}>*/}
          {/*  <ProFormCheckbox noStyle name="autoLogin">*/}
          {/*    自动登录*/}
          {/*  </ProFormCheckbox>*/}
          {/*  <a style={{float: 'right'}}>忘记密码</a>*/}
          {/*</div>*/}
        </LoginForm>

        {/*<Divider>其他登录方式</Divider>*/}
        {/*<Space align="center" size={24} className={styles.other}>*/}
        {/*  <AlipayOutlined className={styles.icon}/>*/}
        {/*  <TaobaoOutlined className={styles.icon}/>*/}
        {/*  <WeiboOutlined className={styles.icon}/>*/}
        {/*</Space>*/}
      </div>
      <div className={styles.footer}>
        ©{new Date().getFullYear()}
      </div>
    </div>
  );
};

export default Login;
