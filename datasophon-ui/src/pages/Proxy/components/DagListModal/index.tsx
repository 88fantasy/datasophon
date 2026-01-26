import CommonTable, { invokeGenOptionCol } from "../../../../components/Common/CommonTable";
import { API } from "../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { Badge, Input, Progress } from "antd";
import { invokePackProtableRequest, METHOD } from "../../../../utils/request";
import type { ProColumns } from "@ant-design/pro-components";
import { T_CANCEL, T_FAILED, T_PENDING, T_RUNNING, T_SUCCESS } from "../../../../components/DagModal/status";
import { blue, gold, green, red } from "@ant-design/colors";
import { invokeGenPath } from "../../../../utils/routerUtils";




const Index = (props, ref) => {

    const {
        current,
        formMapRef,
        record,
        clusterId,
        serviceInstanceId,
        alarmAll,
        className
    } = props



    const columns = useMemo(() => {
        const res: ProColumns[] = [
            {
                dataIndex: 'index',
                title: '序号',
                valueType: 'indexBorder',
                width: 48,
            },
            {
                title: '名称',
                dataIndex: 'dagName',
                search: false,
                ellipsis: true
            },
            {
                title: '状态',
                dataIndex: 'status',
                ellipsis: true,
                search: false,
                render: (text, record,) => {

                    const statusIcon = {
                        [T_SUCCESS]: {
                            style: {
                                color: green.primary
                            },
                            text: '成功'
                        },
                        [T_FAILED]: {
                            style: {
                                color: red.primary
                            },
                            text: '失败'
                        },
                        [T_CANCEL]: {
                            style: {
                                color: gold.primary
                            },
                            text: '已取消'
                        },
                        [T_PENDING]: {
                            style: {
                                color: blue.primary
                            },
                            text: '等待中'
                        },
                        [T_RUNNING]: {
                            style: {
                                color: blue.primary
                            },
                            text: '运行中'
                        }

                    }

                    let res = statusIcon[record.status]?.style?.color || blue.primary
                    text = statusIcon[record.status]?.text


                    res = (
                        <Badge
                            color={res}
                            classNames={{
                                indicator: 'mr-[8px]'
                            }}
                        />
                    )


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
                title: '描述',
                dataIndex: 'description',
                search: false,
                ellipsis: true
            },
            {
                title: '创建时间',
                dataIndex: 'createdTime',
                search: false,
                ellipsis: true
            },
            {
                title: '开始时间',
                dataIndex: 'startedTime',
                search: false,
                ellipsis: true
            },
            {
                title: '完成时间',
                dataIndex: 'completedTime',
                search: false,
                ellipsis: true
            },
            {
                title: '操作',
                valueType: 'option',
                key: 'option',
                width: 80,
                render: invokeGenOptionCol([
                    {
                        title: '查看',
                        onClick: async (text, record, _, action) => {
                            // return onBuildOrEditClick({
                            //     record,
                            //     action
                            // })
                            window.open(invokeGenPath(`/ddh/Dag?dagId=${record.id}`))

                        }
                    }
                ])
            },
        ]



        return res

    }, [])



    return (
        <CommonTable
            tableProps={{
                search: false,
                columns,
                tableAlertRender: false,
                className: `${className} `,

                request: invokePackProtableRequest({
                    api: API.findDagByPage,
                    method: METHOD.GET,
                    params: (params) => {
                        params.clusterId = clusterId

                        return params
                    }
                }),
            }}

        />
    )
};

export default forwardRef(Index) 