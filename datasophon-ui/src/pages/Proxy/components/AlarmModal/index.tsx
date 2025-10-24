import CommonTable, { invokeGenOptionCol } from "../../../../components/Common/CommonTable";
import { API } from "../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { Badge, Input, Progress } from "antd";
import { invokePackProtableRequest } from "../../../../utils/request";
import type { ProColumns } from "@ant-design/pro-components";


const Index = (props, ref) => {

    const {
        current,
        formMapRef,
        record,
        clusterId,
        instanceId,
        alarmAll
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
                title: '主机',
                dataIndex: 'hostname',
                ellipsis: true,
                search: false,
                render: (text, record,) => {

                    const colorMap = {
                        'warning': 'orange',
                    }


                    let res = colorMap[record.alertLevel] || 'red'



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
                title: '告警组',
                dataIndex: 'alertGroupName',
                search: false,
                ellipsis: true
            },
            {
                title: '告警指标',
                dataIndex: 'alertTargetName',
                search: false,
                ellipsis: true
            },
            {
                title: '告警详情',
                dataIndex: 'alertInfo',
                search: false,
                ellipsis: true
            },
            {
                title: '告警时间',
                dataIndex: 'createTime',
                search: false,
                ellipsis: true
            },
            {
                title: '建议操作',
                dataIndex: 'alertAdvice',
                search: false,
                ellipsis: true
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
                request: invokePackProtableRequest({
                    api: () => {
                        return alarmAll
                            ? API.getAllAlertList
                            : API.getAlertList

                    },
                    params: (params) => {
                        if (alarmAll) {
                            params.clusterId = clusterId
                        } else {
                            params = {
                                instanceId
                            }
                        }
                        return params
                    }
                }),
            }}

        />
    )
};

export default forwardRef(Index) 