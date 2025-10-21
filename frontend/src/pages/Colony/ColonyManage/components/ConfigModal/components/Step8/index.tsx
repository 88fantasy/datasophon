import { pickControlPropsWithId, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { API } from "../../../../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosPost } from "../../../../../../../api/request";
import { Input, Progress } from "antd";
import { useConfigContext } from "../../configContext";
import { ExclamationCircleOutlined } from "@ant-design/icons";
import { invokePackProtableRequest } from "../../../../../../../utils/request";





const Index = ({
    current,
    formMapRef,
    record,

}, ref) => {

    const [currentPage, setCurrentPage] = useState(1)
    const timeRef = useRef()
    const actionRef = useRef()
    const [state, setState] = useState({})
    const [selectedRows, setSelectedRows] = useState([])
    const [dataSource, setDataSource] = useState([])
    const { clusterId } = useConfigContext()


    // const invokeUpdateFormData = useCallback((arr, source) => {


    //     arr = arr.filter(
    //         (item) => item.installed
    //     );


    //     formMapRef.current[3]?.current.setFieldsValue({
    //         services: arr,
    //     })
    // }, [formMapRef])



    const invokeInit = useCallback(async (forceUpdate) => {

        const fn = () => {
            actionRef.current?.reload()
            invokeInit(false)
        }

        timeRef.current = setTimeout(() => {
            fn()
        }, forceUpdate ? 0 : 3 * 1000)

    }, [])



    const invokeValid = async () => {
        const fieldValue = formMapRef.current[3]?.current.getFieldsValue()
        const { services } = fieldValue


        if (!services?.length) {
            return {
                valid: false
            }
        }

        const params = {
            clusterId,
            serviceIds: (services || []).map(val => val.id)
        };

        const res = await axiosPost(API.checkServiceDependency, params);

        return {
            valid: res.code === 200,
            msg: res.msg
        }

    }

    const seeDetail = useCallback((record) => {
        if (timeRef.current) {
            clearTimeout(timeRef.current)
            timeRef.current = undefined
        }


    }, [])

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
                render: (text, record, index) => {


                    return currentPage !== 3 ? (
                        <span onClick={() => seeDetail(record)}>
                            {text}
                        </span>
                    ) : (
                        <span>{text}</span>
                    );
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
            // {
            //     title: '版本',
            //     dataIndex: 'serviceVersion',
            //     ellipsis: true,
            //     search: false,
            // },
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
                render: (text, record) => {
                    return (
                        <span
                            onClick={() => seeDetail(record)}
                        >
                            查看日志
                        </span>
                    );
                },
            });
        }


        return res

    }, [currentPage, seeDetail])



    // useEffect(() => {
    //     if (current === 3 && !dataSource?.length) {
    //         invokeInit()
    //     }
    //     // eslint-disable-next-line react-hooks/exhaustive-deps
    // }, [current, dataSource?.length,])

    useEffect(() => {

        invokeInit(true)

        return () => {
            if (timeRef.current) {
                clearTimeout(timeRef.current)
                timeRef.current = undefined
            }
        }
    }, [invokeInit])


    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })

    return (
        <>
            <ProForm.Item
                name="services"
            >
                <CommonTable
                    tableProps={{
                        actionRef: actionRef,
                        search: false,
                        columns,
                        className: 'mb-[20px]',
                        manualRequest: true,
                        tableAlertRender: false,
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
                                const res = {
                                    clusterId,

                                }



                                return res
                            }
                        }),
                        // rowSelection: {
                        //     selectedRowKeys: selectedRows.map(val => val.id),
                        //     onChange: (selectedRowKeys, selectedRows) => {
                        //         setSelectedRows(selectedRows)

                        //         console.log('selectedRows', selectedRows)


                        //         invokeUpdateFormData(selectedRows, dataSource)

                        //     },

                        // },
                    }}

                />
            </ProForm.Item>

        </>
    )
};

export default forwardRef(Index) 