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
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import { account } from '../../utils/account';
import { invokeRelogin } from '../../utils/authorityUtils';
import { invokeGenPath, invokeGetRouteByPath, invokeHandlePath } from '../../utils/routerUtils';
import { memo, use, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API } from '../../api';
import { axiosGet, axiosJsonPost, axiosPost } from '../../api/request';
import { clone, cloneDeep, isEqual, noop } from 'lodash-es';
import { menuRender } from './components/menuRender';
import { ProxyContext } from '../../context/proxyContext';
import { actionsRender } from './components/actionsRender';
import asyncHook from '../../components/Common/CommonModal/asyncHook';

import bg1 from '../../assets/O1CN01O4etvp1DvpFLKfuWq_!!6000000000279-2-tps-609-606.png'
import bg2 from '../../assets/O1CN018NxReL1shX85Yz6Cx_!!6000000005798-2-tps-884-496.png'
import gobalEvent, { uiEvent } from '../../utils/gobalEvent';
import artifactType, { getArtifactTypeLabelByValue } from '../../constants/artifactType';
import { useClusterFromParams } from '../../hooks/useClusterFromParams';
import { T_K8S, T_PHYSICAL } from '../../constants/clusterType';
import { isEmpty } from '../../utils/util';

const showUserCenterModal = asyncHook(() => import('./components/UserCenterModal/api'))

const token = {
    header: {
        colorBgMenuItemSelected: 'rgba(0,0,0,0.04)',

    },
}

const bgLayoutImgList = [
    {
        src: bg1,
        left: 85,
        bottom: 100,
        height: '303px',
    },
    {
        src: bg1,
        bottom: -68,
        right: -45,
        height: '303px',
    },
    {
        src: bg2,
        bottom: 0,
        left: 0,
        width: '331px',
    },
]

const headerTitleRender = (logo) => {
    return (
        <div className='flex gap-[10px] w-[250px]'>
            {
                logo
            }
            VOS
        </div>
    )
}

const settings = {
    layout: 'mix',
    splitMenus: true,
    siderWidth: 250,
    headerTitleRender,
    token,
    bgLayoutImgList
};

let timer

const onClusterManageClick = () => {
    window.location.href = invokeGenPath(`/Colony/ColonyManage`)
}

const onUserClick = async () => {
    const modelApi = await showUserCenterModal()

    modelApi.default({})
}




const Index = () => {

    const { instanceId } = useParams()

    const { clusterId, memoCluster } = useClusterFromParams()

    const serviceListMapRef = useRef({})
    const timeoutIdRef = useRef()
    const matchRoute = useRef(invokeGetRouteByPath())

    const [runningClusterList, setRunningClusterList] = useState()
    const [dashboardUrl, setDashboardUrl] = useState()
    const [serviceList, setServiceList] = useState([])
    const [k8sNamespaceList, setK8sNamespaceList] = useState([])
    const [k8sInstanceListMap, setK8sInstanceListMap] = useState({})

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


    const memoMenuProps = useMemo(() => {
        const res = {}


        if (
            /ServiceManage\/Instance/gi.test(window.location.pathname) &&
            instanceId
        ) {
            res.selectedKeys = [
                `/ddh/Cluster/:clusterId/ServiceManage/Instance/${instanceId}`
            ]
        }


        return res
    }, [instanceId])

    const memoServiceRouteObj = useMemo(() => {
        return route.routes.find(val => {
            return /Cluster\/:clusterId\/ServiceManage/.test(val.path)
        }) || {}
    }, [route.routes])

    const defaultProps = useMemo(() => {

        // const menuProps = {}
        let serviceRoutes = []

        const sortFn = (arr) => {

            serviceRoutes.sort((a, b) => {
                const hasChildrenA = a.children !== undefined;
                const hasChildrenB = b.children !== undefined;

                if (hasChildrenA && !hasChildrenB) {
                    return -1; // A 排前面
                }
                if (!hasChildrenA && hasChildrenB) {
                    return 1; // B 排前面
                }
                return 0; // 顺序不变
            })

        }


        const serviceRouteObj = memoServiceRouteObj


        if (serviceList && (isEmpty(memoCluster?.archType) || memoCluster?.archType === T_PHYSICAL)) {
            // route
            // console.log('route.routes', serviceRouteObj)
            const serviceListMap = {}
            serviceList
                // .filter(val => val.catalog)
                .forEach(val => {
                    if (!serviceListMap[val.catalog]) {
                        serviceListMap[val.catalog] = []
                    }
                    serviceListMap[val.catalog].push(val)
                })

            Object.keys(serviceListMap).forEach(k => {
                let arr = []
                const label = getArtifactTypeLabelByValue(k)
                const val = serviceListMap[k].map(val => {
                    return {
                        name: val.serviceName,
                        path: `${serviceRouteObj.path}/Instance/${val.id}`,
                        originData: val
                    }
                })
                if (artifactType.find(val => val.value === k)) {
                    const obj = {
                        name: label,
                        path: `${serviceRouteObj.path}/${k}`,
                        children: val
                    }

                    arr.push(obj)
                } else {
                    arr = val
                    // arr.push()
                }

                serviceRoutes.push(...arr)
                // val.forEach(v => {
                //     if (v.catalog)
                // })
            })



        } else {
            const arr = k8sNamespaceList.map(namespaceObj => {
                const val = k8sInstanceListMap[namespaceObj.id]?.map(v => {
                    return {
                        name: v.serviceName,
                        path: `${serviceRouteObj.path}/Instance/${v.id}`,
                        originData: v
                    }
                })


                const obj = {
                    name: namespaceObj.namespace,
                    path: `${serviceRouteObj.path}/${namespaceObj.id}`,
                    children: val
                }


                return obj
            })

            serviceRoutes.push(...arr.filter(val => val.children?.length))


        }


        sortFn(serviceRoutes)

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

        return {
            route: cloneDeep(route),
        }
    }, [clusterId, k8sInstanceListMap, k8sNamespaceList, memoCluster, memoServiceRouteObj, route, serviceList])




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


    const invokeGetK8sInstanceList = useCallback(async (namespaceArr = []) => {


        // 遍历每个 namespace，调用实例列表接口
        const instanceMap = {}
        for (const { namespace, id } of namespaceArr) {
            const instanceRes = await axiosJsonPost(API.k8sInstanceQueryInstanceList, {
                namespace,
                clusterId
            })
            if (instanceRes.code === 200) {
                instanceMap[id] = instanceRes.data


                serviceListMapRef.current = instanceRes.data.reduce((acc, val) => {
                    acc[val.id] = val
                    return acc
                }, {})
            }
        }
        setK8sInstanceListMap(instanceMap)

    }, [clusterId])

    const invokeGetK8sNamespaceList = useCallback(async () => {
        const namespaceRes = await axiosGet(`${API.k8sNamespaceListByClusterId}/${clusterId}`)


        if (namespaceRes.code === 200 && namespaceRes.data) {
            setK8sNamespaceList(namespaceRes.data)
            await invokeGetK8sInstanceList(namespaceRes.data)

        }
    }, [clusterId, invokeGetK8sInstanceList])



    const invokeGetServiceList = useCallback(async (hadInit) => {
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
                const res = await fn(!hadInit)
                resolve(res)
            }
        })


        // hadInitRef.current = true

    }, [clusterId, invokeCancelGetServiceList, invokeGetServiceListByClusterProxy])

    const onMenuClick = useCallback((obj: MenuDataItem) => {
        // console.log('onMenuClick.obj', obj)

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
        axiosPost(API.loginOut)
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

    // const invokeGetRunningClusterList = useCallback(async () => {
    //     const res = await axiosPost(API.runningClusterList, {})
    //     if (res.code === 200) {
    //         const arr = res.data.map(item => {
    //             return {
    //                 label: item.clusterName,
    //                 value: item.id,
    //                 originData: item
    //             }
    //         })

    //         setRunningClusterList(arr)
    //         await invokeGetDashboardUrl()
    //     }
    // }, [invokeGetDashboardUrl])



    const invokeInit = useCallback(async () => {

        if (!hadInit) {

            if (memoCluster?.archType === T_K8S) {
                await invokeGetK8sNamespaceList()
            } else {
                await invokeGetServiceList(hadInit)
            }
            // if (!runningClusterList?.length) {
            //     if (/Cluster\/\:clusterId\//gi.test(matchRoute.current.route.path)) {
            //         await invokeGetRunningClusterList()
            //     }
            // }
            await invokeGetDashboardUrl()


            setHadInit(true)
        }

    }, [hadInit, memoCluster?.archType, invokeGetK8sNamespaceList, invokeGetServiceList, invokeGetDashboardUrl])


    const invokeGetServiceListMap = useCallback(() => {
        return serviceListMapRef.current
    }, [])


    const onDeleteClick = useCallback(({ item }) => {
        const {
            originData
        } = item


        const {
            id,
        } = originData


        if (id) {

            if (String(instanceId) === String(id)) {
                const firstItem = memoServiceRouteObj?.routes[0]

                if (firstItem) {
                    // navigate(invokeHandlePath(firstItem.path))
                    onMenuClick(firstItem)
                }
            }


            invokeGetServiceList(false)


        }
    }, [instanceId, invokeGetServiceList, memoServiceRouteObj?.routes, onMenuClick])



    const invokeUpdateServiceList = useCallback(async () => {
        const res = await invokeGetServiceList(false)



        if (!res?.data?.find(val => String(val.id) === instanceId)) {
            const firstItem = memoServiceRouteObj?.routes[0]

            if (firstItem) {
                // navigate(invokeHandlePath(firstItem.path))
                onMenuClick(firstItem)
            }
        }


    }, [instanceId, invokeGetServiceList, memoServiceRouteObj?.routes, onMenuClick])




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
                // runningClusterList,
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
                        // collapsed={false}
                        {...defaultProps}
                        location={{
                            pathname: window.location.pathname,
                        }}
                        avatarProps={memoAvatarProps}
                        actionsRender={actionsRender.bind(noop, {
                            clusterId,
                            invokeUpdateServiceList
                        })}
                        menuItemRender={menuRender.bind(noop, {
                            onMenuClick,
                            onDeleteClick,
                            memoCluster
                            // onDele
                        })}
                        menuProps={memoMenuProps}
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