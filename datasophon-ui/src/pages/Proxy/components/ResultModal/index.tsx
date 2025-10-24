import { pickControlPropsWithId, ProCard, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../components/Common/CommonTable";
import { API } from "../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosPost } from "../../../../api/request";
import { Input, Progress } from "antd";
import { ExclamationCircleOutlined, LeftOutlined, ReconciliationFilled } from "@ant-design/icons";
import { invokePackProtableRequest } from "../../../../utils/request";
import CommonMonacoEditor from "../../../../components/Common/CommonMonacoEditor";



const headerTitleMap = {
    1: '安装并启动服务',

}

const Index = (props, ref) => {

    const {
        current,
        formMapRef,
        record,
        clusterId,
        className = ''
    } = props

    const [currentPage, setCurrentPage] = useState(1)
    const timeRef = useRef()
    const actionRef = useRef()
    const [state, setState] = useState({})
    const stateRef = useRef([])
    const [logs, setLogs] = useState()
    const [selectedRows, setSelectedRows] = useState([])
    const [dataSource, setDataSource] = useState([])



    const invokeInit = useCallback(async (forceUpdate) => {

        const fn = () => {
            actionRef.current?.reload()
            invokeInit(false)
        }

        if (forceUpdate) {
            fn()
        } else {
            timeRef.current = setTimeout(() => {
                fn()
            }, 3 * 1000)
        }

    }, [])



    // const invokeValid = async () => {
    //     const fieldValue = formMapRef.current[3]?.current.getFieldsValue()
    //     const { services } = fieldValue


    //     if (!services?.length) {
    //         return {
    //             valid: false
    //         }
    //     }

    //     const params = {
    //         clusterId,
    //         serviceIds: (services || []).map(val => val.id)
    //     };

    //     const res = await axiosPost(API.checkServiceDependency, params);

    //     return {
    //         valid: res.code === 200,
    //         msg: res.msg
    //     }

    // }

    const seeDetail = useCallback(async (record) => {
        if (timeRef.current) {
            clearTimeout(timeRef.current)
            timeRef.current = undefined
        }


        // if (currentPage === 3) {
        stateRef.current.push(record)
        // }
        setCurrentPage(preState => {
            return preState + 1
        })





        setLogs(undefined)


        if (stateRef.current.length === 3) {
            const res = await axiosPost(API.getHostCommandLog, {
                clusterId,
                hostCommandId: record.hostCommandId
            });

            if (res.code === 200) {
                setLogs(res.data)
            }
        } else {
            actionRef.current?.reloadAndRest()
            invokeInit(true)
        }




        // console.log('actionRef.current', actionRef.current)

    }, [clusterId, invokeInit])

    const columns = useMemo(() => {
        const res: ProColumns[] = [
            {
                dataIndex: 'index',
                title: '序号',
                valueType: 'indexBorder',
                width: 48,
            },
            {
                title: currentPage === 1
                    ? "命令"
                    : currentPage === 2
                        ? "主机"
                        : "指令名称",
                dataIndex: currentPage === 2 ? "hostname" : "commandName",
                ellipsis: true,
                search: false,
                render: (text, record, ...args) => {

                    if (currentPage === 3) {
                        return text
                    }

                    const key = currentPage === 2 ? "hostname" : "commandName"

                    const fn = invokeGenOptionCol([
                        {
                            title: record[key],
                            onClick: async (text, record, _, action) => {

                                if (
                                    currentPage !== 3

                                ) {
                                    seeDetail(record)
                                }
                                // return onRetryClick([record.hostname])
                            }
                        }
                    ])

                    return fn(text, record, ...args)


                }
            },
            {
                title: '状态',
                dataIndex: 'commandProgress',
                search: false,
                render: (text, record) => {
                    return (
                        <span>
                            {record.commandStateCode === 1 ? (
                                <Progress
                                    percent={record.commandProgress}
                                    status="active"
                                />
                            ) : record.commandStateCode === 2 ? (
                                <Progress percent={record.commandProgress} />
                            ) : record.commandStateCode === 4 ? (
                                <Progress
                                    strokeColor='#FFA53D'
                                    format={
                                        () => <ExclamationCircleOutlined color="#FFA53D" />
                                    }
                                    percent={record.commandProgress}
                                />
                            ) : (
                                <Progress
                                    percent={record.commandProgress}
                                    status="exception"
                                />
                            )}
                        </span>
                    );
                }

            },
        ]


        if (currentPage == 1) {
            res.push(
                {
                    title: "开始时间",
                    dataIndex: "createTime",
                    search: false,

                },
                {
                    title: "持续时间",
                    dataIndex: "durationTime",
                    search: false,

                }
            );
        } else if (currentPage === 3) {
            res.push({
                title: "日志信息",
                dataIndex: "resultMsg",
                // width: 140,
                render: invokeGenOptionCol([
                    {
                        title: '查看日志',
                        onClick: (text, record) => {
                            seeDetail(record)
                        }
                    }
                ]),
            });
        }


        return res

    }, [currentPage, seeDetail])

    const invokeGetHeaderTitle = useCallback(() => {
        let res = '安装并启动服务'


        if (stateRef.current.length) {
            const obj = stateRef.current[stateRef.current.length - 1]

            res = obj.hostname || obj.commandName


            if (stateRef.current.length === 3) {
                res = '日志'
            }

            const onClick = () => {





                setCurrentPage(preState => {
                    return preState - 1
                })

                if (timeRef.current) {
                    clearTimeout(timeRef.current)
                    timeRef.current = undefined
                }

                stateRef.current.pop()

                // setTimeout(() => {
                // TODO: 优化
                actionRef.current?.reloadAndRest()

                invokeInit(true)
                // }, 0)

            }

            res = (
                <div className="flex items-center">
                    <LeftOutlined
                        className="mr-[10px]"
                        onClick={onClick}
                    />
                    {res}
                </div>
            )
        }

        return res
    }, [invokeInit])


    useEffect(() => {

        invokeInit(true)

        return () => {
            if (timeRef.current) {
                clearTimeout(timeRef.current)
                timeRef.current = undefined
            }
        }
    }, [invokeInit])

    return (
        <>
            {
                currentPage < 4 ?
                    <CommonTable
                        tableProps={{
                            actionRef: actionRef,
                            search: false,
                            columns,
                            className: `${className || ' mb-[20px] mt-[30px]'} `,
                            manualRequest: true,
                            tableAlertRender: false,
                            headerTitle: invokeGetHeaderTitle(),
                            // rowKey: currentPage === 1 ? 'commandId' : 'hostCommandId',
                            scroll: {
                                x: '60vw'
                            },
                            request: invokePackProtableRequest({
                                api: () => {
                                    return currentPage === 1
                                        ? API.getServiceCommandlist
                                        : currentPage === 2
                                            ? API.getServiceHostList
                                            : API.getServiceRoleOrderList;

                                },
                                params: (params) => {
                                    // TODO:
                                    // const res = {
                                    //     clusterId,
                                    // }

                                    params.clusterId = clusterId
                                    const obj = stateRef.current[stateRef.current.length - 1]
                                    if (currentPage === 2) {
                                        params.commandId = obj.commandId
                                    } else if (currentPage === 3) {
                                        params.hostname = obj.hostname
                                        params.commandHostId = obj.commandHostId
                                    }



                                    return params
                                }
                            }),
                        }}

                    /> :
                    <ProCard
                        className={`${className || 'h-[70vh] !mt-[30px]'}  `}
                        title={invokeGetHeaderTitle()}
                        bordered={true}
                    >
                        <CommonMonacoEditor
                            language="yaml"
                            value={logs}
                            options={{
                                minimap: { enabled: false },
                                readOnly: true,
                                wordWrap: 'on',         // 启用自动换行
                                automaticLayout: true,
                            }}
                        />
                    </ProCard>
            }


        </>
    )
};

export default forwardRef(Index) 