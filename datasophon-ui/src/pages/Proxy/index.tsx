import {
    AppstoreOutlined,
    ClockCircleOutlined,
    GithubFilled,
    InfoCircleFilled,
    LogoutOutlined,
    PlusCircleFilled,
    QuestionCircleFilled,
    SearchOutlined,
    UserOutlined,
} from '@ant-design/icons';
import type { MenuDataItem, ProSettings } from '@ant-design/pro-components';
import { PageContainer, ProCard, ProLayout } from '@ant-design/pro-components';
import { Alert, Badge, Button, Dropdown, Input, Space } from 'antd';
import { routes, invokeGenMenuByPattern, menu, menuMap } from '../../routes';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { account } from '../../utils/account';
import { invokeRelogin } from '../../utils/authorityUtils';
import { invokeGenPath, invokeGetRouteByPath, invokeHandlePath } from '../../utils/routerUtils';
import { ClusterGlobalProvider } from '../../context/clusterGlobalContext';
import { memo, use, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API } from '../../api';
import { axiosPost } from '../../api/request';
import { cloneDeep, isEqual, noop } from 'lodash-es';
import { menuRender } from './components/menuRender';
import { ProxyContext } from '../../context/proxyContext';
import { actionsRender } from './components/actionsRender';
import asyncHook from '../../components/Common/CommonModal/asyncHook';

const showUserCenterModal = asyncHook(() => import('./components/UserCenterModal/api'))

const token = {
    header: {
        colorBgMenuItemSelected: 'rgba(0,0,0,0.04)',

    },
}

const bgLayoutImgList = [
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
]

const settings: ProSettings | undefined = {
    layout: 'mix',
    splitMenus: true,
};

let timer

const onClusterManageClick = () => {
    const firstRoute = routes.find(val => {
        return val.auth !== 0
    })



    window.location.href = firstRoute.path
    // console.log('firstRoute', firstRoute)
    // window.location.replace()
}

const onUserClick = async () => {
    const modelApi = await showUserCenterModal()

    modelApi.default({})
}

const Index = () => {

    const { clusterId, } = useParams()
    const hadInitRef = useRef()
    const serviceListMapRef = useRef({})
    const timeoutIdRef = useRef()
    const matchRoute = useRef(invokeGetRouteByPath())

    const [runningClusterList, setRunningClusterList] = useState()
    const [dashboardUrl, setDashboardUrl] = useState()
    const [serviceList, setServiceList] = useState([])

    const [proCardBodyStyle, setProCardBodyStyle] = useState({})

    const [hadInit, setHadInit] = useState(() => {
        return !clusterId
    })

    // if (!clusterId) {
    //     hadInitRef.current = true
    // }


    // const [hadInit, setHadInit] = useState(false)

    const user = account.getUser()

    const navigate = useNavigate();
    // const defaultProps = {
    //     route: invokeGenMenuByPattern().route
    // };


    const {
        route
    } = invokeGenMenuByPattern()

    const defaultProps = useMemo(() => {



        if (serviceList.length) {
            // route
            const serviceRouteObj = route.routes.find(val => {
                return /Cluster\/:clusterId\/ServiceManage/.test(val.path)
            })
            console.log('route.routes', serviceRouteObj)

            const serviceRoutes = serviceList.map(val => {
                return {
                    name: val.serviceName,
                    path: `${serviceRouteObj.path}/Instance/${val.id}`,
                    originData: val
                }
            })


            serviceRoutes.unshift({
                name: '总览',
                path: `${serviceRouteObj.path}/Instance/Overview`,
                originData: {
                    serviceStateCode: -1,
                    serviceList,
                    clusterId,

                }
            })

            serviceRouteObj.routes = serviceRoutes
        }

        // console.log('defaultProps', cloneDeep(route))
        return {
            route
        }
    }, [clusterId, route, serviceList])




    const invokeCancelGetServiceList = useCallback(() => {
        if (timeoutIdRef.current) {
            clearTimeout(timeoutIdRef.current)
            timeoutIdRef.current = undefined
        }

    }, [])


    const invokeGetServiceListByClusterProxy = useCallback(async () => {
        const res = await axiosPost(API.getServiceListByCluster, {
            clusterId
        })

        if (res.code === 200) {
            setServiceList(preState => {


                if (!isEqual(res.data, preState)) {
                    return res.data
                }
                return preState
            })
            serviceListMapRef.current = res.data.reduce((acc, val) => {
                acc[val.id] = val
                return acc
            }, {})
        }
        return res
    }, [clusterId])

    const invokeGetServiceList = useCallback(async (hadInit) => {
        console.log('invokeGetServiceList', hadInit)

        const fn = async (forceUpdate) => {
            let res
            if (forceUpdate) {
                res = await invokeGetServiceListByClusterProxy()
                fn(false)
            } else {
                timeoutIdRef.current = setTimeout(async () => {
                    await invokeGetServiceListByClusterProxy()
                    invokeCancelGetServiceList()
                    fn(false)
                }, 3 * 1000)
            }

            return res
        }

        return new Promise(async (resolve) => {

            invokeCancelGetServiceList()

            if (clusterId) {
                await fn(!hadInit)
                resolve()
            }
        })


        // hadInitRef.current = true

    }, [clusterId, invokeCancelGetServiceList, invokeGetServiceListByClusterProxy])

    const onMenuClick = useCallback((obj: MenuDataItem) => {

        let {
            path
        } = obj

        if (!path) {
            return
        }


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
    }, [navigate]);

    const onLogoutClick = useCallback(() => {
        account.clear()
        invokeRelogin()
    }, [])


    const invokeGetDashboardUrl = useCallback(async () => {

        const res = await axiosPost(API.getDashboardUrl, { clusterId })

        if (res.code === 200) {
            clearTimeout(timer)
            setDashboardUrl(res.data)
        }

        // .then(res => {
        //     // commit('setDashboardUrl', res.data)
        //     // dispatch('getServiceList')
        // })
    }, [clusterId])

    const invokeGetRunningClusterList = useCallback(async () => {
        const res = await axiosPost(API.runningClusterList, {})
        if (res.code === 200) {
            const arr = res.data.map(item => {
                return {
                    label: item.clusterName,
                    value: item.id,
                    originData: item
                }
            })

            setRunningClusterList(arr)
            await invokeGetDashboardUrl()
        }
    }, [invokeGetDashboardUrl])



    const invokeInit = useCallback(async () => {

        if (!hadInit) {
            await invokeGetServiceList(hadInit)
            if (!runningClusterList?.length) {
                if (/Cluster\/\:clusterId\//gi.test(matchRoute.current.route.path)) {
                    await invokeGetRunningClusterList()
                }
            }

            setHadInit(true)
        }

    }, [hadInit, invokeGetRunningClusterList, invokeGetServiceList, runningClusterList?.length])


    const invokeGetServiceListMap = useCallback(() => {
        return serviceListMapRef.current
    }, [])




    const memoAvatarProps = useMemo(() => {
        return {
            src: user.avatar,
            size: 'small',
            title: user.username,
            render: (props, dom) => {
                return (
                    <Dropdown
                        menu={{
                            items: [
                                {
                                    key: 'userCenter',
                                    icon: <UserOutlined />,
                                    label: '个人中心',
                                    onClick: onUserClick
                                },
                                {
                                    key: 'onClusterManageClick',
                                    icon: <AppstoreOutlined />,
                                    label: '集群管理',
                                    onClick: onClusterManageClick
                                },
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
        }
    }, [onLogoutClick, user.avatar, user.username])


    useEffect(() => {
        invokeInit()
    }, [invokeInit])

    useEffect(() => {
        return invokeCancelGetServiceList()
    }, [invokeCancelGetServiceList])


    return (
        <ProxyContext.Provider
            value={{
                setProCardBodyStyle,
                invokeGetServiceListMap,
                serviceListMapRef,
                runningClusterList,
                dashboardUrl,
                clusterId,
            }}
        >

            <div
                id="test-pro-layout"
                style={{
                    height: '100vh',
                }}
            >
                {
                    hadInit && <ProLayout
                        className='h-[100vh]'
                        headerTitleRender={
                            (logo) => {
                                return (
                                    <div className='flex gap-[10px] w-[250px]'>
                                        {
                                            logo
                                        }
                                        VOS
                                    </div>
                                )
                            }
                        }

                        token={token}
                        bgLayoutImgList={bgLayoutImgList}
                        {...defaultProps}
                        location={{
                            pathname: window.location.pathname,
                        }}
                        siderWidth={250}
                        avatarProps={memoAvatarProps}
                        actionsRender={actionsRender.bind(noop, {
                            clusterId
                        })}
                        menuItemRender={menuRender.bind(noop, {
                            onMenuClick
                        })}

                        {...settings}
                    >
                        {
                            (
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
                                </PageContainer>
                            )
                        }
                    </ProLayout>
                }
            </div>
        </ProxyContext.Provider>


    );
};

export default memo(Index)