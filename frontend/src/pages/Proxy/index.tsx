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
import { menu, menuMap } from '../../routes';
import { Outlet, useNavigate } from 'react-router-dom';
import { account } from '../../utils/account';
import { invokeRelogin } from '../../utils/authorityUtils';


const defaultProps = {
    route: menu.route
};

const Index = () => {
    const settings: ProSettings | undefined = {
        layout: 'mix',
        splitMenus: true,
    };

    const user = account.getUser()

    const navigate = useNavigate();


    const onMenuClick = (obj: MenuDataItem) => {

        let {
            path
        } = obj

        if (!path) {
            return
        }



        if (/^(http|\/\/)/.test(path)) {
            window.open(path);
        } else {
            const menuObj = menuMap[path]
            if (menuObj?.routes?.length) {
                path = menuObj.routes[0].path
            }
            navigate(path)
        }
    };

    const onLogoutClick = () => {
        account.clear()
        invokeRelogin()
    }


    return (
        <>
            <div
                id="test-pro-layout"
                style={{
                    height: '100vh',
                }}
            >
                <ProLayout
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
                    menuItemRender={(item, dom) => (
                        <div
                            onClick={() => {
                                onMenuClick(item)
                            }}
                        >
                            {dom}
                        </div>
                    )}
                    {...settings}
                >
                    <PageContainer
                        title={false}
                    >
                        <ProCard
                            style={{
                                minHeight: 800,
                            }}
                        >
                            <Outlet />
                        </ProCard>
                    </PageContainer>
                </ProLayout>
            </div>
        </>
    );
};

export default Index