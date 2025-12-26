import type { ProColumns } from "@ant-design/pro-components";
import type { GithubIssueItem } from "../../../../../../../components/Common/CommonTable";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { invokePackProtableRequest } from "../../../../../../../utils/request";
import { API } from "../../../../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { invokeMapValue } from "../../../../../../../utils/listUtils";
import { axiosPost } from "../../../../../../../api/request";
import { showMsgAfferRequest } from "../../../../../../../utils/util";
import CommonBtnList from "../../../../../../../components/Common/CommonBtnList";
import { message, Tag } from "antd";
import { useConfigContext } from "../../configContext";




const Index = ({
    current,
    formMapRef,
    record,
    index
}, ref) => {

    const actionRef = useRef()
    const invokeStartAutoUpdateId = useRef()
    const [selectedRows, setSelectedRows] = useState([])

    const { clusterId } = useConfigContext()


    console.log('formMapRef', formMapRef)

    const steps1Data = formMapRef.current[0]?.current?.getFieldsValue() || {}


    const invokeClearStartAutoUpdateId = useCallback(() => {
        if (invokeStartAutoUpdateId.current) {
            clearTimeout(invokeStartAutoUpdateId.current)
            invokeStartAutoUpdateId.current = undefined
        }
    }, [])

    const invokeStartAutoUpdate = useCallback(() => {


        invokeClearStartAutoUpdateId()

        invokeStartAutoUpdateId.current = setTimeout(() => {
            actionRef.current?.reload?.()
            invokeStartAutoUpdate()
        }, 3 * 1000)
    }, [invokeClearStartAutoUpdateId])

    const onRetryClick = useCallback(async (rows) => {
        const hostnames = rows.join(',')
        const params = {
            hostnames,
            clusterId,
            sshUser: steps1Data.sshUser,
            sshPort: steps1Data.sshPort,
        };

        const res = await axiosPost(API.rehostCheck, params)

        showMsgAfferRequest(res)
        if (res.code === 200) {
            setSelectedRows([])
            invokeStartAutoUpdate()
        }
    }, [clusterId, invokeStartAutoUpdate, steps1Data.sshPort, steps1Data.sshUser])

    const toolBarRender = useCallback(() => {
        const list = [
            {
                label: '重试',
                type: 'primary',
                onClick: () => {
                    if (!selectedRows?.length) {
                        message.warning("请至少选择一台主机！");
                    } else {
                        onRetryClick(selectedRows.map(val => val.hostname))

                    }

                },

            }
        ]
        return (
            <CommonBtnList
                list={list}
            />
        )
    }, [onRetryClick, selectedRows])

    const invokeValid = useCallback(async () => {
        const params = {
            clusterId,
        };

        const res = await axiosPost(API.hostCheckCompleted, params);


        if (
            !res.hostCheckCompleted 
        ) {
            res.msg = '存在为未检验成功的主机'
        }

        res.valid = res.hostCheckCompleted

        return res

    }, [clusterId])

    const columns: ProColumns[] = useMemo(() => {
        return [
            {
                dataIndex: 'index',
                title: '序号',
                valueType: 'indexBorder',
                width: 48,
            },
            {
                title: '主机',
                dataIndex: 'hostname',
                ellipsis: true,
            },
            {
                title: '当前受管',
                dataIndex: 'managed',
                search: false,
                ellipsis: true,
                render: (text) => {
                    return <span>{text ? "是" : "否"}</span>;

                }
            },
            {
                title: '检测结果',
                dataIndex: 'checkResult.code',
                ellipsis: true,
                search: false,
                render: (text, record) => {
                    let res = invokeMapValue(record, 'checkResult.msg')



                    const code = record.checkResult.code

                    if (code) {
                        const colorMap = {
                            10001: 'green',
                            10000: 'blue'
                        }


                        const color = colorMap[code] || 'red'

                        res = (
                            <Tag
                                color={color}
                            >
                                {
                                    res
                                }
                            </Tag>
                        )
                    }


                    return res



                }
            },

            {
                title: '操作',
                valueType: 'option',
                key: 'option',
                width: 200,
                render: invokeGenOptionCol([
                    {
                        title: '重试',
                        disabled: (text, row) => {
                            return row.userType === 1
                        },
                        onClick: async (text, record, _, action) => {
                            return onRetryClick([record.hostname])
                        }
                    }
                ])
            },
        ]
    }, [onRetryClick]);


    useEffect(() => {
        if (current === index) {
            actionRef.current?.reload?.()
            invokeStartAutoUpdate()
        } else if (index + 1 === current || index - 1 === current) {
            invokeClearStartAutoUpdateId()
        }

        return () => {
            invokeClearStartAutoUpdateId()
        }
    }, [current, index, invokeClearStartAutoUpdateId, invokeStartAutoUpdate])

    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })

    return (
        <CommonTable
            tableProps={{
                actionRef: actionRef,
                search: false,
                request: invokePackProtableRequest({
                    api: API.analysisHostList,
                    params: {
                        clusterId,
                        ...(steps1Data)
                    }
                    ,
                }),
                columns,
                className: 'mb-[20px]',
                manualRequest: true,
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

export default forwardRef(Index) 