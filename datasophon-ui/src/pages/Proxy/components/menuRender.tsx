import { Badge, Dropdown } from "antd"
import { invokeGetRouteByPath } from "../../../utils/routerUtils"
import { isEmpty, showComfirmModal, showMsgAfferRequest } from "../../../utils/util"
import { MoreOutlined } from "@ant-design/icons"
import { axiosJsonPost, axiosPost } from "../../../api/request"
import { API } from "../../../api"
import asyncHook from "../../../components/Common/CommonModal/asyncHook"
import { T_SETPS_TYPE_ADDSERVICE } from "../../Colony/ColonyManage/components/ConfigModal/stepType"
import { gray } from "@ant-design/colors"

const showConfigModal = asyncHook(() =>
    import("../../Colony/ColonyManage/components/ConfigModal/api"));

const showResultModal = asyncHook(() =>
    import("./ResultModal/api"));

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


const invokeRenderDot = ({
    obj,
    item,
    dom,
    isServiceManage
}) => {

    const isOverview = /Instance\/Overview/gi.test(item.path)


    return isServiceManage &&
        <Badge
            // count={
            //     <ClockCircleOutlined style={{ color: '#f5222d' }} />
            // }
            classNames={{
                indicator: '!w-[8px] !h-[8px]'
            }}
            color={
                isEmpty(badgeColorMap[item.originData?.serviceStateCode]) && !isOverview ? 'red' : badgeColorMap[item.originData?.serviceStateCode]
            }
        />
}

const invokeRenderMore = ({
    obj,
    item,
    dom,
    isServiceManage
}) => {


    if (isServiceManage) {

        const {

            originData,
            name
        } = item

        const isOverview = /Instance\/Overview/gi.test(item.path)


        const {
            id,
            clusterId,
            serviceList
        } = originData

        console.log('serviceList', serviceList)

        const items = isOverview ? [
            {
                label: '添加服务',
                key: T_ADD_SERVICE
            },
            {
                label: '启动所有服务',
                key: T_STARTALL
            },
            {
                label: '停止所有服务',
                key: T_STOPALL
            },
            {
                label: '重启所有需要重启的服务',
                key: T_RESTARTALL
            }
        ] : [
            {
                label: '启动',
                key: T_START_SERVICE,
            },
            {
                label: '停止',
                key: T_STOP_SERVICE
            },
            {
                label: '重启',
                key: T_RESTART_SERVICE
            },
            {
                label: '删除',
                key: T_DELETE_SERVICE
            }

        ]
        const onClick = async (obj) => {

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
                        clusterId
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
                        API.generateServiceCommand,
                        {
                            serviceInstanceIds,
                            clusterId,
                            commandType: typeMap[obj.key]
                        }
                    )

                    // .then((res) => {
                    //     if (res.code === 200) {
                    //         this.$message.success("操作成功");
                    //         // todo: 打开头部那个setting栏
                    //         this.$destroyAll()
                    //         this.showClusterSetting(true)
                    //     }
                    // });

                } else if (obj.key === 'DELETE_SERVICE') {
                    res = await axiosPost(
                        API.clusterServiceInstanceDelete,
                        {
                            serviceInstanceId: id
                        }
                    )
                } else {
                    res = await axiosPost(
                        API.generateServiceCommand,
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

                    if (obj.key !== 'DELETE_SERVICE') {
                        const modelApi = await showResultModal()

                        modelApi.default({
                            clusterId,
                        })
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
        onMenuClick
    } = obj

    // console.log('item', item)

    // if (item.path)


    const isServiceManage = invokeGetRouteByPath(item.path)?.route.path === '/ddh/Cluster/:clusterId/ServiceManage/Instance/:instanceId'


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

            <div className="flex-1">
                {dom}
            </div>
            {
                invokeRenderMore({ obj, item, dom, isServiceManage })
            }
        </div>
    )
}