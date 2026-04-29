
import { API } from '../../api';
import { axiosGet, axiosJsonPost } from '../../api/request';
import asyncHook from '../../components/Common/CommonModal/asyncHook';
import { formatMomentObj2YYYYMMDDHHMMSS } from '../../utils/dateTime';
import CommonTable from '../Common/CommonTable';
const showCommonLogModal = asyncHook(() =>
    import("../../components/Common/CommonLogModal/api"))


export const invokeShowLogs = async ({
    serviceInstanceId,
    commandId,
    clusterId,
    archType
}, e) => {

    e?.stopPropagation()
    const instanceId = serviceInstanceId
    if (commandId && clusterId) {
        const [modelApi, deploymentRes, podRes] = await Promise.all([
            showCommonLogModal(),
            axiosJsonPost(API.k8sInstanceListResource, {
                instanceId,
                resourceType: 'deployment'
            }),
            axiosJsonPost(API.k8sInstanceListResource, {
                instanceId,
                resourceType: 'pod'
            })
        ])
        modelApi.default({
            archType,
            api: [
                {
                    label: '执行日志',
                    api: () => {
                        return axiosGet(`${API.getK8sExecLog}/${commandId}`)
                    }
                },
                {
                    label: '运行日志',
                    children: podRes?.data?.map(val => {
                        return {
                            label: val.name,
                            value: val.name,
                            children: val.containerNames?.map(containerNamesObj => {
                                return {
                                    label: containerNamesObj,
                                    value: containerNamesObj,
                                }
                            }),
                            originData: val
                        }
                    }) || [],
                    api: (body) => {
                        return axiosJsonPost(`${API.getK8sRuntimeLog}`, {
                            instanceId,
                            containerName: body[1],
                            podName: body[0]
                        })
                    },

                },
                {
                    label: '事件tag',
                    children: deploymentRes?.data?.map(val => {
                        return {
                            label: val.name,
                            value: val.name,
                            originData: val
                        }
                    }) || [],
                    api: (body) => {
                        return axiosJsonPost(`${API.getK8sEvents}`, {
                            instanceId,
                            deployment: body.pop()
                        })
                    },
                    logRender: ({
                        logs,
                        key
                    }) => {
                        const columns = [
                            {
                                title: '类型',
                                dataIndex: 'type',
                                key: 'type',
                                hideInSearch: true,
                            },
                            {
                                title: '原因',
                                dataIndex: 'reason',
                                key: 'reason',
                                hideInSearch: true,
                            },
                            {
                                title: '首次发生时间',
                                dataIndex: 'firstTimestamp',
                                key: 'firstTimestamp',
                                hideInSearch: true,
                                render: (val) => formatMomentObj2YYYYMMDDHHMMSS(val),
                            },
                            {
                                title: '最后发生时间',
                                dataIndex: 'lastTimestamp',
                                key: 'lastTimestamp',
                                hideInSearch: true,
                                render: (val) => formatMomentObj2YYYYMMDDHHMMSS(val),
                            },
                            {
                                title: '次数',
                                dataIndex: 'count',
                                key: 'count',
                                hideInSearch: true,
                            },
                            {
                                title: '消息',
                                dataIndex: 'message',
                                key: 'message',
                                hideInSearch: true,
                            },
                        ]
                        return (
                            <CommonTable
                                key={key}
                                tableProps={{
                                    columns,
                                    dataSource: logs,
                                    toolBarRender: false,
                                    tableAlertRender: false,
                                    search: false,

                                }}
                            />
                        )
                    }
                },

            ]
        })
    } else {
        console.warn('没有hostCommandId,clusterId hostCommandId:',
            commandId,
            'clusterId:',
            clusterId
        )
    }




}