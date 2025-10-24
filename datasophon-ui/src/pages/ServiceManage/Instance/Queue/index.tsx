
import { API } from '../../../../api';
import { invokePackProtableRequest } from '../../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../../api/request';
import { noop } from 'lodash-es';
import { useParams } from 'react-router';
import type { ProColumns } from '@ant-design/pro-components';
import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { Empty } from 'antd';
import CommonBtnList from '../../../../components/Common/CommonBtnList';
import { PlusOutlined } from '@ant-design/icons';
import { showMsgAfferRequest } from '../../../../utils/util';


const showBuildOrEditModal = () =>
    import("./BuildOrEditModal/api");

const onBuildOrEditClick = async ({
    action,
    record
}) => {
    const modelApi = await showBuildOrEditModal()

    modelApi.default({
        record,
        onOk: () => {
            action?.reload?.()
        }
    })
}

const coreRender = ({
    core,
    mem
}, text, record) => {
    return `${record[core]}Core, ${record[mem]}GB`
}

const columns: ProColumns[] = [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '队列名称',
        dataIndex: 'queueName',
        ellipsis: true,
    },
    {
        title: '最小资源数',
        dataIndex: 'minMem',
        search: false,
        ellipsis: true,
        render: coreRender.bind(noop, {
            core: 'minCore',
            mem: 'minMem'
        }),

    },
    {
        title: '最大资源数',
        dataIndex: 'maxCore',
        ellipsis: true,
        search: false,
        render: coreRender.bind(noop, {
            core: 'maxCore',
            mem: 'maxMem'
        }),
    },
    {
        title: '资源分配策略',
        dataIndex: 'schedulePolicy',
        ellipsis: true,
        search: false,
    },
    {
        title: '权重',
        dataIndex: 'weight',
        ellipsis: true,
        search: false,
    },
    {
        title: '是否允许资源被抢占',
        dataIndex: 'allowPreemption',
        ellipsis: true,
        search: false,
        render: (text, record) => {
            return record.allowPreemption === 1 ? '是' : '否'
        }
    },
    {
        title: 'AM占用比例',
        dataIndex: 'amShare',
        ellipsis: true,
        search: false,
    },
    {
        title: '操作',
        valueType: 'option',
        key: 'option',
        width: 200,
        render: invokeGenOptionCol([
            {
                title: '编辑',
                onClick: async (text, record, _, action) => {
                    return onBuildOrEditClick({
                        record,
                        action
                    })
                }
            },
            {
                title: '删除',
                titleKey: 'username',
                onClick: async (text, record) => {
                    const params = JSON.stringify([record.id])

                    return axiosPostUpload(API.deleteQueue, params)
                }
            },
        ])
    },
];

const Index = () => {

    const { clusterId } = useParams()
    const actionRef = useRef()

    const [showGraph, setShowGraph] = useState()


    const invokeInit = useCallback(async () => {
        const res = await axiosPost(API.clusterYarnSchedulerInfo, {
            clusterId
        })

        if (res.code === 200) {
            setShowGraph(res.data === 'capacity')
        }

        return res
    }, [clusterId])

    const onRefreshClick = useCallback(async () => {
        const ajaxApi = showGraph ? API.refreshQueuesYARN : API.refreshQueues
        const res = await axiosPost(ajaxApi, {
            clusterId,
        })

        showMsgAfferRequest(res)
        if (res.code === 200) {
            // this.getQueueList();
            await invokeInit()


            if (res.data === 'capacity') {
                // TODO:
            } else {
                actionRef.current?.reload()
            }
        }
    }, [clusterId, invokeInit, showGraph])

    const toolBarRender = useCallback(() => {



        const list = [
            {
                label: '刷新队列',
                onClick: onRefreshClick
            },
            {
                label: '新建',
                type: 'primary',
                icon: <PlusOutlined />,
                onClick: onBuildOrEditClick.bind(noop, {
                    action: actionRef.current
                }),
            },

        ]
        return (
            <CommonBtnList
                list={list}
            />
        )
    }, [onRefreshClick])



    useEffect(() => {
        invokeInit()
    }, [invokeInit])

    return !showGraph ? (
        <CommonTable
            tableProps={{
                actionRef,
                request: invokePackProtableRequest({
                    api: API.getQueueList,
                    params: {
                        clusterId
                    }
                }),
                columns,
                search: false,
                toolBarRender
            }}
        />
    ) : <Empty />
};

export default memo(Index)