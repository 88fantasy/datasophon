import {
    GithubFilled,
    InfoCircleFilled,
    LogoutOutlined,
    PlusCircleFilled,
    QuestionCircleFilled,
    SearchOutlined,
} from '@ant-design/icons';
import type { MenuDataItem, ProSettings } from '@ant-design/pro-components';
import { PageContainer, ProCard, ProLayout } from '@ant-design/pro-components';
import { Alert, Button, Dropdown, Input, Space } from 'antd';
import { invokeGenMenuByPattern, menu, menuMap } from '../../routes';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { account } from '../../utils/account';
import { invokeRelogin } from '../../utils/authorityUtils';
import { invokeGenPath, invokeHandlePath } from '../../utils/routerUtils';
import { ClusterGlobalProvider } from '../../context/clusterGlobalContext';
import { memo, useCallback, useEffect, useMemo, useState } from 'react';


const Index = () => {


    const settings: ProSettings | undefined = {
        layout: 'mix',
        splitMenus: true,
    };

    const [proCardBodyStyle, setProCardBodyStyle] = useState({})

    const user = account.getUser()

    const navigate = useNavigate();
    // const defaultProps = {
    //     route: invokeGenMenuByPattern().route
    // };

    const defaultProps = useMemo(() => {
        return {
            route: invokeGenMenuByPattern().route
        }
    }, [])


    const onMenuClick = (obj: MenuDataItem) => {

        let {
            path
        } = obj

        if (!path) {
            return
        }


        console.log('onMenuClick', obj)




        if (/^(http|\/\/)/.test(path)) {
            window.open(invokeGenPath(path));
        } else {
            const menuObj = menuMap[path]
            if (menuObj?.routes?.length) {
                path = menuObj.routes[0].path
            }
            // invokeGetRouteByPath(path)
            // invokgen
            navigate(invokeHandlePath(path))
            setProCardBodyStyle({})
        }
    };

    const onLogoutClick = useCallback(() => {
        account.clear()
        invokeRelogin()
    }, [])


    // useEffect(() => {
    //     if()
    // }, [])


    return (
        <ClusterGlobalProvider
            config={{
                setProCardBodyStyle
            }}
        >
            <div
                id="test-pro-layout"
                style={{
                    height: '100vh',
                }}
            >
                <ProLayout
                    className='h-[100vh]'
                    headerTitleRender={
                        (logo) => {
                            return (
                                <div className='flex gap-[10px] mr-[40px]'>
                                    {
                                        logo
                                    }
                                    DataSophon
                                </div>
                            )
                        }
                    }

                    token={{

                        header: {
                            colorBgMenuItemSelected: 'rgba(0,0,0,0.04)',

                        },
                    }}
                    bgLayoutImgList={[
                        {
                            src: 'https://img.alicdn.com/imgextra/i2/O1CN01O4etvp1DvpFLKfuWq_!!6000000000279-2-tps-609-606.png',
                            left: 85,
                            bottom: 100,
                            height: '303px',
                        },
                        {
                            src: 'https://img.alicdn.com/imgextra/i2/O1CN01O4etvp1DvpFLKfuWq_!!6000000000279-2-tps-609-606.png',
                            bottom: -68,
                            right: -45,
                            height: '303px',
                        },
                        {
                            src: 'https://img.alicdn.com/imgextra/i3/O1CN018NxReL1shX85Yz6Cx_!!6000000005798-2-tps-884-496.png',
                            bottom: 0,
                            left: 0,
                            width: '331px',
                        },
                    ]}
                    {...defaultProps}
                    location={{
                        pathname: window.location.pathname,
                    }}

                    avatarProps={{
                        src: user.avatar,
                        size: 'small',
                        title: user.username,
                        render: (props, dom) => {
                            return (
                                <Dropdown
                                    menu={{
                                        items: [
                                            {
                                                key: 'logout',
                                                icon: <LogoutOutlined />,
                                                label: '退出登录',
                                                onClick: onLogoutClick
                                            },
                                        ],
                                    }}
                                >
                                    {dom}
                                </Dropdown>
                            );
                        },
                    }}
                    menuItemRender={(item, dom) => {
                        return (

                            <div
                                onClick={() => {
                                    onMenuClick(item)
                                }}
                            >
                                {dom}
                            </div>
                        )
                    }}
                    {...settings}
                >
                    <PageContainer
                        title={false}
                        className='h-full'
                    >
                        <ProCard
                            style={{
                                minHeight: '83vh',
                            }}
                            bodyStyle={{
                                height: '100%',
                                ...proCardBodyStyle
                            }}
                        // className='p-[0]'

                        >
                            <Outlet />
                        </ProCard>
                        {/* <Outlet /> */}

                    </PageContainer>
                </ProLayout>
            </div>
        </ClusterGlobalProvider>

    );
};

export default Index