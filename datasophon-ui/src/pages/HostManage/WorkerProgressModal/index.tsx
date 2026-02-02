import type { ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../components/Common/CommonTable";
import { invokePackProtableRequest } from "../../../utils/request";
import { API } from "../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { message, Progress, Tag } from "antd";




const Index = ({
    current,
    record,
    index,
    clusterId
}) => {

    const actionRef = useRef()
    const invokeStartAutoUpdateId = useRef()




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
        ]
    }, []);


    useEffect(() => {
        invokeStartAutoUpdate()
        return () => {
            invokeClearStartAutoUpdateId()
        }
    }, [current, index, invokeClearStartAutoUpdateId, invokeStartAutoUpdate])

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
                tableAlertRender: false,
            }}

        />
    )
};

export default Index 