
import { AlertOutlined, MoreOutlined, SettingOutlined } from "@ant-design/icons"
import { useCallback } from "react"


const showResultModal = () =>
    import("./ResultModal/api");
const showAlarmModal = () =>
    import("./AlarmModal/api");



export const actionsRender = (props) => {




    const {
        clusterId,
    } = props

    // console.log('item', item)

    // if (item.path)



    // const isServiceManage = invokeGetRouteByPath(item.path)?.route.path === '/ddh/Cluster/:clusterId/ServiceManage/Instance/:instanceId'


    const onSettingClick = async (obj) => {
        const modelApi = await showResultModal()

        modelApi.default({
            clusterId,
        })
    }

    const onAlarmClick = async (obj) => {
        const modelApi = await showAlarmModal()

        modelApi.default({
            clusterId,
            alarmAll: true
        })
    }


    return clusterId && [
        <SettingOutlined
            onClick={onSettingClick}
        />,
        <AlertOutlined
            onClick={onAlarmClick}
        />
    ]
}