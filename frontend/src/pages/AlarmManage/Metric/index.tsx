
import { API } from '../../../api';
import { invokePackProtableRequest } from '../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../api/request';
import { useParams } from 'react-router';
import type { ProColumns } from '@ant-design/pro-components';
import { useCallback } from 'react';


const showFormModal = () =>
    import("./BuildOrEditModal/api");



const columns: ProColumns[] = [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '名称',
        dataIndex: 'alertGroupName',
        ellipsis: true,
    },
    {
        title: '模板类别',
        dataIndex: 'alertGroupCategory',
        search: false,
        ellipsis: true,
    },
    {
        title: '告警指标数',
        dataIndex: 'alertQuotaNum',
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
                title: '查看告警指标',
                onClick: () => {

                }
            },
            {
                title: '删除',
                titleKey: 'alertGroupName',
                onClick: async (text, record) => {

                    const params = JSON.stringify([record.id]);

                    return axiosPostUpload(API.deleteGroup, params)
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
                    api: API.getAlarmGroupList,
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

export default Index