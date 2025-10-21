
import { API } from '../../../../api';
import { invokePackProtableRequest } from '../../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../../components/Common/CommonTable';
import { Badge, Button, Dropdown, message, Progress, Tag } from 'antd';
import { noop } from 'lodash-es';
import { useParams } from 'react-router';
import { act, useEffect, useRef, useState } from 'react';
import { showComfirmModal } from '../../../../utils/util';
import { axiosPost } from '../../../../api/request';
import type { ProColumns } from '@ant-design/pro-components';



// const showHandRackModal = () =>
//     import("./HandRackModal/api");
// const showAddLabelModal = () =>
//     import("./AddLabelModal/api");
const showAddCharacterModal = () =>
    import("./AddCharacterModal/api");

const showCommonLogModal = () =>
    import("../../../../components/Common/CommonLogModal/api");



const serviceRoleStateMap = {
    1: '正在运行',
    2: '停止',
    3: '告警',
    4: '退役中',
    5: '已退役',
}

const Index = () => {
    const actionRef = useRef()
    const [selectedRows, setSelectedRows] = useState([])
    const [roleType, setRoleType] = useState({})
    const [roleGroupName, setRoleGroupName] = useState({})

    const { clusterId, instanceId } = useParams()


    const columns: ProColumns[] = [
        {
            dataIndex: 'index',
            title: '序号',
            valueType: 'indexBorder',
            width: 48,
        },

        {
            title: '角色类型',
            dataIndex: 'serviceRoleName',
            ellipsis: true,
            valueEnum: roleType,
            render: (text, record) => {
                const colorMap = {
                    1: 'green',
                    2: 'red',
                }


                let res = colorMap[record.serviceRoleStateCode]


                if (res) {
                    res = (
                        <Badge
                            color={res}
                            classNames={{
                                indicator: 'mr-[8px]'
                            }}
                        />
                    )
                } else {
                    res = undefined
                }


                res = (
                    <div>
                        {
                            res
                        }
                        {
                            text
                        }
                    </div>
                )

                return res
            }
        },
        {
            title: '主机',
            dataIndex: 'hostname',
            ellipsis: true,
        },
        {
            title: '角色组',
            dataIndex: 'roleGroupName',
            valueEnum: roleGroupName,
            ellipsis: true,
        },
        {
            title: '状态',
            dataIndex: 'serviceRoleState',
            ellipsis: true,
            valueEnum: serviceRoleStateMap,
            render: (text, record) => {

                console.log('record', record)

                const colorMap = {
                    1: 'success',
                    2: 'error',
                }


                const stateStr = serviceRoleStateMap[record.serviceRoleStateCode] || '存在告警'

                const color = colorMap[record.serviceRoleStateCode] || 'warning'

                return <Tag color={color}>{stateStr}</Tag>
            }
        },
        {
            title: '操作',
            valueType: 'option',
            key: 'option',
            width: 200,
            render: invokeGenOptionCol([
                {
                    title: '查看日志',
                    onClick: async (text, record, _, action) => {
                        // return onBuildOrEditClick({
                        //     record,
                        //     action
                        // })
                        const modelApi = await showCommonLogModal()

                        modelApi.default({
                            api: () => {
                                return axiosPost(API.getLog, {
                                    serviceRoleInstanceId: record.id
                                })
                            }
                        })
                    }
                }
            ])
        },
    ];


    const onBuildClick = async () => {
        // const modelApi = await showConfigModal()

        // modelApi.default({
        //     clusterId,
        //     stepsType: T_STEPS_TYPE_HOSTMANAGE,
        //     record: {}
        // })
    }

    const onAddCharacterModalClick = async () => {
        const modelApi = await showAddCharacterModal()

        modelApi.default({
            serviceInstanceId: instanceId,
            record: {}
        })
    }

    const toolBarRender = () => {


        const invokePreClick = async (fn) => {
            if (!selectedRows.length) {
                return message.warning("请至少选择一个实例");
            }

            const res = await fn?.()


            if (res) {
                setSelectedRows([])

                actionRef.current?.reload();
            }



        }


        const invokeOpService = async (commandType) => {
            const params = {
                serviceInstanceId: instanceId,
                commandType,
                clusterId,
                serviceRoleInstancesIds: selectedRows.map(val => val.id).join(","),
            };

            return axiosPost(API.generateServiceRoleCommand, params)
        }

        // const invokeOpWorder = async (commandType) => {
        //     const params = {
        //         clusterHostIds: selectedRows.map(val => val.hostname).join(","),
        //         commandType
        //     };

        //     return axiosPost(API.generateHostAgentCommand, params)
        // }

        const items = [
            {
                label: '启动',
                onClick: invokePreClick.bind(noop, async () => {


                    const res = await showComfirmModal()
                    if (res) {
                        return invokeOpService('START_SERVICE')
                    }

                }),
            },
            {
                label: '停止',
                onClick: invokePreClick.bind(noop, async () => {
                    const res = await showComfirmModal()
                    if (res) {
                        return invokeOpService('STOP_SERVICE')
                    }
                }),
            },
            {
                label: '重启',
                onClick: invokePreClick.bind(noop, async () => {
                    const res = await showComfirmModal()

                    if (res) {


                        return invokeOpService('RESTART_SERVICE')
                    }

                }),
            },
            {
                label: '分配角色组',
                // onClick: invokePreClick.bind(noop, async () => {
                //     const res = await showComfirmModal()

                //     if (res) {
                //         return invokeOpWorder('stop')
                //     }

                // }),
            },
            {
                label: '删除',
                onClick: invokePreClick.bind(
                    noop,
                    async () => {

                        const res = await showComfirmModal({
                            okType: 'danger'
                        })

                        if (res) {
                            const params = {
                                serviceRoleInstancesIds: selectedRows.map(val => val.id).join(","),
                            };

                            return axiosPost(API.deleteExample, params)
                        }
                    }
                )
            },

        ].map((val, i) => {
            return {
                key: i,
                ...val,

            }
        })

        return (
            <>

                <Dropdown
                    menu={{
                        items,
                        // onClick: (...args) => {
                        //     console.log(args);
                        // }
                    }}
                >
                    <Button>
                        选择操作
                    </Button>

                </Dropdown>
                <Button
                    type="primary"
                    onClick={onBuildClick}
                >
                    添加新实例
                </Button>
                <Button
                    type="primary"
                    onClick={onAddCharacterModalClick}
                >
                    添加角色组
                </Button>
            </>
        )
    }

    const getServiceRoleType = async () => {
        const params = {
            serviceInstanceId: instanceId,
        }
        //角色组类型


        const [
            getServiceRoleTypeRes,
            getRoleGroupListRes,
        ] = await Promise.all([
            axiosPost(API.getServiceRoleType, params),
            axiosPost(API.getRoleGroupList, params)
        ])


        if (getServiceRoleTypeRes.code === 200) {
            setRoleType(
                getServiceRoleTypeRes.data.reduce((pre, aft) => {
                    pre[aft.id] = aft.serviceRoleName
                    return pre
                }, {})
            )

        }


        if (getRoleGroupListRes.code === 200) {
            setRoleGroupName(

                getRoleGroupListRes.data.reduce((pre, aft) => {
                    pre[aft.id] = aft.roleGroupName
                    return pre
                }, {})
            )
        }
    }

    useEffect(() => {
        getServiceRoleType()
    }, [])


    return (
        <CommonTable
            tableProps={{
                actionRef,
                columns,
                request: invokePackProtableRequest({
                    api: API.instanceList,
                    params: (params, sort) => {
                        return {
                            ...params,
                            roleGroupId: params.roleGroupName || '',
                            serviceRoleState: params.serviceRoleName || '',
                            serviceInstanceId: instanceId
                        }
                    }
                }),
                tableAlertRender: false,
                rowSelection: {
                    selectedRowKeys: selectedRows.map(val => val.id),
                    onChange: (selectedRowKeys, selectedRows) => {
                        // console.log(selectedRowKeys, selectedRows);
                        setSelectedRows(selectedRows)
                    },
                },
                toolBarRender
            }}
        />
    )
};

export default Index