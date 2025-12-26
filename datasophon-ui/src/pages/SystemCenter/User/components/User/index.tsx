
import { API } from '../../../../../api';
import { invokePackProtableRequest } from '../../../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../../../api/request';
import { useParams } from 'react-router';
import type { ProColumns } from '@ant-design/pro-components';
import { memo, useCallback } from 'react';
import asyncHook from '../../../../../components/Common/CommonModal/asyncHook';


const showFormModal = asyncHook(() =>
    import("./BuildOrEditModal/api"));



const columns: ProColumns[] = [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '用户名',
        dataIndex: 'username',
        ellipsis: true,
    },
    {
        title: '主用户组',
        dataIndex: 'mainGroup',
        search: false,
        ellipsis: true,
    },
    {
        title: '附属用户组',
        dataIndex: 'otherGroups',
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
                disabled: (text, record) => {
                    return record.userType === 1
                },
                title: '删除',
                titleKey: 'username',
                onClick: async (text, record) => {
                    const params = {
                        id: record.id
                    }

                    return axiosPost(API.clusterUserDelete, params)
                }
            },
        ])
    },
];

const Index = () => {

    const { clusterId } = useParams()

    const onBuildOrEditClick = useCallback(async ({
        action,
        record
    }) => {
        const modelApi = await showFormModal()

        modelApi.default({
            record,
            clusterId,
            onOk: () => {
                action?.reload?.()
            }
        })
    }, [clusterId])

    return (
        <CommonTable
            tableProps={{
                request: invokePackProtableRequest({
                    api: API.getTenant,
                    params: {
                        clusterId
                    }
                }),
                columns,
                onBuildClick: onBuildOrEditClick,
            }}
        />
    )
};

export default memo(Index)