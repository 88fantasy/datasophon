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
import { message, Progress, Tag } from "antd";
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

        const res = await axiosPost(API.reStartDispatcherHostAgent, params)

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

        const res = await axiosPost(API.dispatcherHostAgentCompleted, params);


        if (
            !res.dispatcherHostAgentCompleted
        ) {
            res.msg = '存在为未分发完成的主机'
        }

        res.valid = res.dispatcherHostAgentCompleted

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
                title: '进度',
                dataIndex: 'progress',
                search: false,
                ellipsis: true,

                render: (text, row,) => {
                    const {
                        installStateCode
                    } = row
                    const statusMap = {
                        1: 'active',
                        2: undefined
                    }

                    const status = Object.prototype.hasOwnProperty.call(statusMap, installStateCode) ? statusMap[installStateCode] : 'exception'
                    return (
                        <Progress
                            status={status}
                            percent={row.progress}
                        />
                    )

                }
            },
            {
                title: '进度信息',
                dataIndex: 'message',
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
                        title: '重试',
                        disabled: (text, row) => {
                            return row.installStateCode !== 3
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
                    api: API.dispatcherHostAgentList,
                    params: {
                        clusterId,
                    }
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