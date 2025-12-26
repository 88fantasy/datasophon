
import { AlertOutlined, MoreOutlined, SettingOutlined, UploadOutlined } from "@ant-design/icons"
import { Tooltip } from "antd";
import { noop } from "antd/es/_util/warning";
import asyncHook from "../../../components/Common/CommonModal/asyncHook";


const showResultModal = asyncHook(() =>
    import("./ResultModal/api"));
const showAlarmModal = asyncHook(() =>
    import("./AlarmModal/api"));

const showUploadDeployModal = asyncHook(() => import('../../../components/UploadDeployModal/api'))
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



    const onImportClick = async (record) => {
        const modelApi = await showUploadDeployModal()
        modelApi.default({
            record
        })
    }



    return clusterId && [
        <Tooltip
            key="2"
            title="上传部署文件包"
        >
            <UploadOutlined
                onClick={onImportClick.bind(noop, {})}
            />
        </Tooltip>,
        <Tooltip
            key="1"
            title="安装并启动服务进度">
            <SettingOutlined
                onClick={onSettingClick}
            />
        </Tooltip>,
        <Tooltip
            key="0"
            title="告警情况"
        >
            <AlertOutlined
                onClick={onAlarmClick}
            />
        </Tooltip>,



    ]
}