import type { ProColumns } from "@ant-design/pro-components";
import type { GithubIssueItem } from "../../../../../../../components/Common/CommonTable";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { invokePackProtableRequest } from "../../../../../../../utils/request";
import { API } from "../../../../../../../api";
import { useEffect, useRef } from "react";
import { invokeMapValue } from "../../../../../../../utils/listUtils";




const columns: ProColumns<GithubIssueItem>[] = [
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
            return invokeMapValue(record, 'checkResult.msg')
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
                onClick: async (text, record, _, action) => {
                    action?.reload()
                }
            }
        ])
    },
];

const Index = ({
    current,
    formMapRef,
    record
}) => {

    const actionRef = useRef()
    const invokeStartAutoUpdateId = useRef()


    console.log('formMapRef', formMapRef)

    const params = {
        clusterId: record.id,
        ...(formMapRef.current[0]?.current?.getFieldsValue() || {})
    }



    const invokeClearStartAutoUpdateId = () => {
        if (invokeStartAutoUpdateId.current) {
            clearTimeout(invokeStartAutoUpdateId.current)
            invokeStartAutoUpdateId.current = undefined
        }
    }

    const invokeStartAutoUpdate = () => {


        invokeClearStartAutoUpdateId()

        invokeStartAutoUpdateId.current = setTimeout(() => {
            actionRef.current?.reload?.()
            invokeStartAutoUpdate()
        }, 3 * 1000)
    }

    useEffect(() => {
        if (current === 1) {
            actionRef.current?.reload?.()
            invokeStartAutoUpdate()
        }

        return () => {
            invokeClearStartAutoUpdateId()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [current])

    return (
        <CommonTable
            tableProps={{
                actionRef: actionRef,
                search: false,
                request: invokePackProtableRequest({
                    api: API.analysisHostList,
                    params,
                }),
                columns,
                className: 'mb-[20px]',
                manualRequest: true
            }}

        />
    )
};

export default Index