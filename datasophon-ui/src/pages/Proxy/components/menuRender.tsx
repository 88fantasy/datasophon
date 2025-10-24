import { Badge, Dropdown } from "antd"
import { invokeGetRouteByPath } from "../../../utils/routerUtils"
import { isEmpty, showComfirmModal, showMsgAfferRequest } from "../../../utils/util"
import { MoreOutlined } from "@ant-design/icons"
import { axiosPost } from "../../../api/request"
import { API } from "../../../api"


const showResultModal = () =>
    import("./ResultModal/api");

const badgeColorMap = {
    1: ' ',
    2: 'green',
    3: 'orange',
}

const invokeRenderItem = ({
    obj,
    item,
    dom
}) => {

}


const invokeRenderDot = ({
    obj,
    item,
    dom,
    isServiceManage
}) => {
    return isServiceManage &&
        <Badge
            // count={
            //     <ClockCircleOutlined style={{ color: '#f5222d' }} />
            // }
            classNames={{
                indicator: '!w-[8px] !h-[8px]'
            }}
            color={
                isEmpty(badgeColorMap[item.originData?.serviceStateCode]) ? 'red' : badgeColorMap[item.originData?.serviceStateCode]
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


        const {
            id,
            clusterId
        } = originData

        const items = [
            {
                label: '启动',
                key: 'START_SERVICE',
            },
            {
                label: '停止',
                key: 'STOP_SERVICE'
            },
            {
                label: '重启',
                key: 'RESTART_SERVICE'
            },
            {
                label: '删除',
                key: 'DELETE_SERVICE'
            }

        ]
        const onClick = async (obj) => {

            console.log('obj', obj, item)
            const menuItem = items.find(val => val.key === obj.key)
            let res = await showComfirmModal({
                content: `确定要${menuItem.label}${name}吗？`,
                okType: 'danger'
            })

            if (res) {
                if (obj.key === 'DELETE_SERVICE') {
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