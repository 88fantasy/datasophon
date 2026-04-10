
import { AlertOutlined, MoreOutlined, PartitionOutlined, SettingOutlined, UploadOutlined } from "@ant-design/icons"
import { Dropdown, Tooltip } from "antd";
import { noop } from "antd/es/_util/warning";
import asyncHook from "../../../components/Common/CommonModal/asyncHook";


const showResultModal = asyncHook(() =>
    import("./ResultModal/api"));
const showAlarmModal = asyncHook(() =>
    import("./AlarmModal/api"));

const showUploadDeployModal = asyncHook(() => import('../../../components/UploadDeployModal/api'))
const showUploadDeployConfigModal = asyncHook(() => import('../../../components/UploadDeployConfigModal/api'))
const showDagListModal = asyncHook(() => import('./DagListModal/api'))
export const actionsRender = (props) => {




    const {
        clusterId,
        invokeUpdateServiceList,
        memoCluster
    } = props

    // console.log('item', item)

    // if (item.path)



    // const isServiceManage = invokeGetRouteByPath(item.path)?.route.path === '/ddh/Cluster/:clusterId/ServiceManage/Instance/:instanceId'


    const onSettingClick = async (obj) => {
        const modelApi = await showResultModal()

        modelApi.default({
            clusterId,
            memoCluster
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
            record,
            onCancelClick: () => {
                invokeUpdateServiceList()
            }
        })
    }


    const onImportDeployManifestClick = async (record) => {
        const modelApi = await showUploadDeployConfigModal()
        modelApi.default({
            record,
            memoCluster,
            onOk: () => {
                invokeUpdateServiceList()
            }
        })
    }

    const onDagListClick = async (record) => {
        const modelApi = await showDagListModal()
        modelApi.default({
            clusterId
            // onOk: () => {
            //     invokeUpdateServiceList()
            // }
        })
    }



    const menuItems = [
        {
            label: '部署包',
            onClick: onImportClick.bind(noop, {
                id: clusterId
            })
        },
        {
            label: '部署清单',
            onClick: onImportDeployManifestClick.bind(noop, {
                id: clusterId
            })

        }
    ].map(val => {
        val.key = val.label

        return val
    })



    return clusterId && [
        <Tooltip
            key="3"
            title="部署状况"
        >
            <PartitionOutlined
                onClick={onDagListClick}
            />
        </Tooltip>,
        <Dropdown
            key="2"

            menu={{
                items: menuItems
            }}
        >
            <UploadOutlined
            />
        </Dropdown>,
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