import { Badge, Dropdown } from "antd"
import { invokeGenPath, invokeGetRouteByPath } from "../../../utils/routerUtils"
import { isEmpty, showComfirmModal, showMsgAfferRequest } from "../../../utils/util"
import { AlertOutlined, DashboardOutlined, MoreOutlined, ReloadOutlined } from "@ant-design/icons"
import { axiosGet, axiosJsonPost, axiosPost } from "../../../api/request"
import { API } from "../../../api"
import asyncHook from "../../../components/Common/CommonModal/asyncHook"
import { T_SETPS_TYPE_ADDSERVICE } from "../../Colony/ColonyManage/components/ConfigModal/stepType"
import { gray, orange, yellow } from "@ant-design/colors"
import { conversionSubmitValue } from "@ant-design/pro-components"
import { T_K8S } from "../../../constants/clusterType"
import { noop } from "lodash-es"

const showConfigModal = asyncHook(() =>
    import("../../Colony/ColonyManage/components/ConfigModal/api"));

const showResultModal = asyncHook(() =>
    import("./ResultModal/api"));


const showAlarmModal = asyncHook(() =>
    import("./AlarmModal/api"));

const showCompareModal = asyncHook(() =>
    import("./CompareModal/api"));


const badgeColorMap = {
    '-1': ' ',
    1: gray.primary,
    2: 'green',
    3: 'orange',
}





const T_ADD_SERVICE = 'ADD_SERVICE'
const T_STARTALL = 'STARTALL'
const T_STOPALL = 'STOPALL'
const T_RESTARTALL = 'RESTARTALL'
const T_START_SERVICE = 'START_SERVICE'
const T_STOP_SERVICE = 'STOP_SERVICE'
const T_RESTART_SERVICE = 'RESTART_SERVICE'
const T_DELETE_SERVICE = 'DELETE_SERVICE'


const k8sBadgeColorMap = {
    0: yellow[5],
    1: 'green',
    2: 'red',
}

const invokeRenderDot = ({
    obj,
    item,
    dom,
    isServiceManage
}) => {

    const isOverview = /Instance\/Overview/gi.test(item.path)


    if (isOverview) {
        return <DashboardOutlined />
    }

    const isK8s = item.originData?.archType === T_K8S
    let color: string
    const colorMap = isK8s ? k8sBadgeColorMap : badgeColorMap

    color = isEmpty(colorMap[item.originData?.serviceStateCode]) && !isOverview ? 'red' : colorMap[item.originData?.serviceStateCode]


    return isServiceManage &&
        <Badge
            // count={
            //     <ClockCircleOutlined style={{ color: '#f5222d' }} />
            // }
            classNames={{
                indicator: '!w-[8px] !h-[8px]'
            }}
            color={color}
        />
}

const invokeRenderMore = ({
    obj,
    item,
    dom,
    isServiceManage,
    memoCluster
}) => {


    if (isServiceManage) {

        const {

            originData,
            name
        } = item

        const {
            onDeleteClick
        } = obj

        const isOverview = /Instance\/Overview/gi.test(item.path)


        const {
            id,
            clusterId,
            serviceList
        } = originData

        console.log('serviceList', serviceList)

        const onItemClick = (e) => {
            e.domEvent.stopPropagation()
        }

        const items = isOverview ? [
            {
                label: '添加服务',
                key: T_ADD_SERVICE,
                onClick: onItemClick
            },
            {
                label: '启动所有服务',
                key: T_STARTALL,
                onClick: onItemClick

            },
            {
                label: '停止所有服务',
                key: T_STOPALL,
                onClick: onItemClick
            },
            {
                label: '重启所有需要重启的服务',
                key: T_RESTARTALL,
                onClick: onItemClick
            }
        ] : [
            {
                label: '启动',
                key: T_START_SERVICE,
                onClick: onItemClick
            },
            {
                label: '停止',
                key: T_STOP_SERVICE,
                onClick: onItemClick
            },
            {
                label: '重启',
                key: T_RESTART_SERVICE,
                onClick: onItemClick
            },
            {
                label: '删除',
                key: T_DELETE_SERVICE,
                onClick: onItemClick
            }

        ]
        const onClick = async (obj, e) => {
            console.log('obj', obj, e)
            // e.stopPropagation()

            // console.log('obj', obj, item)
            const menuItem = items.find(val => val.key === obj.key)
            let res = true

            if (obj.key !== T_ADD_SERVICE) {
                res = await showComfirmModal({
                    content: `确定要${menuItem.label}${!isOverview ? name : ''}吗？`,
                    okType: 'danger'
                })

            }

            if (res) {
                if (obj.key === T_ADD_SERVICE) {
                    const modelApi = await showConfigModal()

                    modelApi.default({
                        stepsType: T_SETPS_TYPE_ADDSERVICE,
                        clusterId,
                        memoCluster
                    })

                    return
                } else if (
                    [T_STARTALL, T_STOPALL, T_RESTARTALL].includes(obj.key)

                ) {

                    const typeMap = {
                        [T_STARTALL]: T_START_SERVICE,
                        [T_STOPALL]: T_STOP_SERVICE,
                        [T_RESTARTALL]: T_RESTART_SERVICE
                    }

                    const serviceInstanceIds = serviceList
                        .filter(val => {

                            if (obj.key === T_RESTARTALL) {
                                return val.needRestart
                            }

                            return val
                        })
                        .map(val => val.id)
                        .join(',')

                    res = await axiosPost(
                        API.generateAndExecSrvInstCmd,
                        {
                            serviceInstanceIds,
                            clusterId,
                            commandType: typeMap[obj.key]
                        }
                    )

                } else if (obj.key === T_DELETE_SERVICE) {
                    let api

                    if (memoCluster.archType === T_K8S) {
                        api = axiosJsonPost.bind(noop, `${API.k8sInstanceRemoveInstanceId}/${id}`)
                    } else {
                        api = axiosJsonPost.bind(noop, API.clusterServiceInstanceDelete, {
                            serviceInstanceId: id
                        })
                    }
                    res = await api()
                } else {
                    res = await axiosPost(
                        API.generateAndExecSrvInstCmd,
                        {
                            commandType: obj.key,
                            serviceInstanceIds: id,
                            clusterId
                        }
                    )
                }



                showMsgAfferRequest(res)


                if (res.code === 200) {
                    // TODO:

                    if (obj.key !== T_DELETE_SERVICE) {

                        if ([T_STARTALL, T_STOPALL, T_RESTARTALL].includes(obj.key)) {
                            window.open(invokeGenPath(`/ddh/Dag?dagId=${res.data || ''}`))
                        } else {
                            const modelApi = await showResultModal()

                            modelApi.default({
                                clusterId,
                                memoCluster,
                                serviceName: item.name
                            })
                        }

                    } else {
                        onDeleteClick?.({ item })
                    }
                }
            }


        }


        return (
            <Dropdown
                menu={{
                    items,
                    onClick
                }}

            >
                <MoreOutlined />
            </Dropdown>
        )
    }

}

export const menuRender = (obj, item, dom) => {

    const {
        onMenuClick,
        memoCluster
    } = obj

    // console.log('item', item)

    // if (item.path)

    const {
        originData = {}
    } = item

    // console.log('originData', originData)

    // const isOverview = /Instance\/Overview/gi.test(item.path)

    const isServiceManage = invokeGetRouteByPath(item.path)?.route.path === '/ddh/Cluster/:clusterId/ServiceManage/Instance/:instanceId'



    const onAlarmClick = async (e) => {
        e.stopPropagation()
        const modelApi = await showAlarmModal()

        modelApi.default({
            serviceInstanceId: originData.id
            // alarmAll: true
        })
    }


    const onRefreshClick = async (e) => {
        e.stopPropagation()
        const modelApi = await showCompareModal()
        console.log('item', item)

        modelApi.default({
            // clusterId,
            serviceInstanceId: originData.id
        })
    }


    return (

        <div
            key={item.path}
            onClick={() => {
                onMenuClick(item)
            }}
            className='flex items-center gap-[8px]'
        >

            {
                invokeRenderDot({ obj, item, dom, isServiceManage })
            }

            <div className="flex-1 flex items-center justify-between overflow-hidden">
                <div className="flex-1 overflow-hidden">
                    {dom}
                </div>
                {
                    !!originData.alertNum && (

                        <div
                            style={{
                                color: orange.primary
                            }}
                            className="flex items-center gap-[2px] "
                            onClick={onAlarmClick}
                        >
                            <AlertOutlined className="text-[19px]" />
                            {
                                originData.alertNum
                            }
                        </div>
                    )
                }
                {
                    originData.needRestart &&
                    <ReloadOutlined
                        className="!ml-[4px]"
                        style={{
                            color: gray[2]
                        }}
                        onClick={onRefreshClick}
                    />
                }

            </div>
            {
                invokeRenderMore({ obj, item, dom, isServiceManage, memoCluster })
            }
        </div>
    )
}