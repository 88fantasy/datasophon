
import { API } from '../../api';
import { invokePackProtableRequest } from '../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../components/Common/CommonTable';
import { Button, Dropdown, message, Progress, Tag } from 'antd';
import { noop } from 'lodash-es';
import { useParams } from 'react-router';
import { act, useRef, useState } from 'react';
import { showComfirmModal } from '../../utils/util';
import { axiosPost } from '../../api/request';
import { T_STEPS_TYPE_HOSTMANAGE } from '../Colony/ColonyManage/components/ConfigModal';
import asyncHook from '../../components/Common/CommonModal/asyncHook';



const showHandRackModal = asyncHook(() =>
    import("./HandRackModal/api"));
const showAddLabelModal = asyncHook(() =>
    import("./AddLabelModal/api"));
const showRoleModal = asyncHook(() =>
    import("./RoleModal/api"));

const showConfigModal = asyncHook(() =>
    import("../Colony/ColonyManage/components/ConfigModal/api"));



const hostStateMap = {
    1: '正常',
    2: '掉线',
    3: '存在告警'
}

const Index = () => {
    const actionRef = useRef()
    const [selectedRows, setSelectedRows] = useState([])

    const { clusterId } = useParams()

    const memoryRender = ({ usedKey, totalKey }, text, record) => {
        const usedMem = record[usedKey] ? record[usedKey] : 0;
        const totalMem = record[totalKey] ? record[totalKey] : 0;
        const percent = (usedMem / totalMem).toFixed(2) * 100;
        return (
            <div>
                <div className="text-[12px]">
                    {usedMem}GB/{totalMem}GB
                </div>
                <Progress
                    percent={percent}
                    status='active'
                    showInfo={false}
                    strokeColor={
                        percent < 70
                            ? "#01AA72"
                            : percent < 90
                                ? "#FF7E01"
                                : "#FF5656"
                    } />
            </div>
        )
    }

    const columns: ProColumns[] = [
        {
            dataIndex: 'index',
            title: '序号',
            valueType: 'indexBorder',
            width: 48,
        },

        {
            title: '主机名',
            dataIndex: 'hostname',
            ellipsis: true,
            sorter: true,
        },
        {
            title: 'IP地址',
            dataIndex: 'ip',
            ellipsis: true,
        },
        {
            title: '状态',
            dataIndex: 'hostState',
            ellipsis: true,
            valueEnum: hostStateMap,
            render: (text, record) => {

                const colorMap = {
                    1: 'success',
                    2: 'error',
                }


                const stateStr = hostStateMap[record.hostState] || '存在告警'

                const color = colorMap[record.hostState] || 'default'

                return <Tag color={color}>{stateStr}</Tag>
            }
        },
        {
            title: '创建时间',
            dataIndex: 'createTime',
            ellipsis: true,
            search: false,
        },
        {
            title: '核数',
            dataIndex: 'coreNum',
            ellipsis: true,
            search: false,
        },
        {
            title: '内存使用',
            dataIndex: 'usedMem',
            ellipsis: true,
            search: false,
            sorter: true,
            render: memoryRender.bind(noop, {
                usedKey: 'usedMem',
                totalKey: 'totalMem'
            }),
        },
        {
            title: '磁盘使用',
            dataIndex: 'usedDisk',
            ellipsis: true,
            search: false,
            sorter: true,
            render: memoryRender.bind(noop, {
                usedKey: 'usedDisk',
                totalKey: 'totalDisk'
            }),
        },
        {
            title: '平均负载',
            dataIndex: 'averageLoad',
            ellipsis: true,
            search: false,
            sorter: true,

        },
        {
            title: '标签',
            dataIndex: 'nodeLabel',
            ellipsis: true,
            search: false,
        },
        {
            title: '机架',
            dataIndex: 'rack',
            ellipsis: true,
            search: false,
        },
        {
            title: 'Cpu架构',
            dataIndex: 'cpuArchitecture',
            ellipsis: true,
            valueEnum: {
                x86_64: 'x86_64',
                aarch64: 'aarch64',
            }
        },
        {
            title: '角色',
            dataIndex: 'serviceRoleNum',
            ellipsis: true,
            search: false,
            render: (text, record, ...args) => {
                return invokeGenOptionCol([
                    {
                        title: record.serviceRoleNum,
                        onClick: async (text, record) => {
                            const modelApi = await showRoleModal()

                            modelApi.default({
                                clusterId,
                                record,
                            })
                        }
                    },

                ])(text, record, ...args)
            }
        }
    ];


    const onBuildClick = async () => {
        const modelApi = await showConfigModal()

        modelApi.default({
            clusterId,
            stepsType: T_STEPS_TYPE_HOSTMANAGE,
            record: {}
        })
    }

    const toolBarRender = () => {


        const invokePreClick = async (fn) => {
            if (!selectedRows.length) {
                return message.warning("请至少选择一台主机！");
            }

            const res = await fn?.()


            if (res) {
                setSelectedRows([])

                actionRef.current?.reload();
            }



        }


        const invokeOpService = async (commandType) => {
            const params = {
                clusterHostIds: selectedRows.map(val => val.hostname).join(","),
                commandType
            };

            return axiosPost(API.generateHostServiceCommand, params)
        }

        const invokeOpWorder = async (commandType) => {
            const params = {
                clusterHostIds: selectedRows.map(val => val.hostname).join(","),
                commandType
            };

            return axiosPost(API.generateHostAgentCommand, params)
        }

        const items = [
            {
                label: '启动主机服务',
                onClick: invokePreClick.bind(noop, async () => {


                    const res = await showComfirmModal()
                    if (res) {
                        return invokeOpService('start')
                    }

                }),
            },
            {
                label: '停止主机服务',
                onClick: invokePreClick.bind(noop, async () => {
                    const res = await showComfirmModal()
                    if (res) {
                        return invokeOpService('stop')
                    }
                }),
            },
            {
                label: '启动主机Worker',
                onClick: invokePreClick.bind(noop, async () => {
                    const res = await showComfirmModal()

                    if (res) {


                        return invokeOpWorder('start')
                    }

                }),
            },
            {
                label: '停止主机Worker',
                onClick: invokePreClick.bind(noop, async () => {
                    const res = await showComfirmModal()

                    if (res) {
                        return invokeOpWorder('stop')
                    }

                }),
            },
            {
                label: '重新安装Workder',
                onClick: invokePreClick.bind(noop, async () => {


                    const res = await showComfirmModal()

                    if (res) {
                        const params = {
                            hostnames: selectedRows.map(val => val.hostname).join(","),
                            clusterId
                        };

                        return axiosPost(API.reStartDispatcherHostAgent, params)
                    }

                }),
            },
            {
                label: '分配标签',
                onClick: invokePreClick.bind(noop, async () => {

                    const modelApi = await showAddLabelModal()

                    return new Promise((resolve) => {
                        modelApi.default({
                            clusterId,
                            hostIds: selectedRows.map(val => val.id).join(","),
                            onOk: resolve
                        })
                    })

                }),
            },
            {
                label: '分配机架',
                onClick: invokePreClick.bind(noop, async () => {

                    const modelApi = await showHandRackModal()

                    return new Promise((resolve) => {
                        modelApi.default({
                            clusterId,
                            hostIds: selectedRows.map(val => val.id).join(","),
                            onOk: resolve
                        })
                    })

                }),
            },
            {
                label: '删除',
                onClick: invokePreClick.bind(
                    noop,
                    async () => {

                        const res = await showComfirmModal({
                            content: '确定要删除所选机架吗？',
                            okType: 'danger'
                        })

                        if (res) {
                            const params = {
                                hostIds: selectedRows.map(val => val.id).join(","),
                            };

                            return axiosPost(API.deleteRack, params)
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
                    type='primary'
                    onClick={onBuildClick}
                >
                    新建
                </Button>
            </>
        )
    }




    return (
        <CommonTable
            tableProps={{
                actionRef,
                columns,
                request: invokePackProtableRequest({
                    api: API.getHostListByPage,
                    params: (params, sort) => {

                        const orderFieldMap = {
                            hostname: 'hostname',
                            usedMem: 'used_mem',
                            usedDisk: 'used_disk',
                            averageLoad: 'average_load',
                        }

                        const orderField = sort ? orderFieldMap[Object.keys(sort)[0]] || '' : ''

                        let orderType = ''
                        if (orderField) {
                            orderType = sort ? (sort[Object.keys(sort)[0]] === 'ascend' ? 'asc' : 'desc') : ''

                        }

                        if (orderType) {
                            orderType = orderType.replace('end', '')
                        }

                        return {
                            ...params,
                            clusterId,
                            orderField,
                            orderType
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