
import { API } from '../../../api';
import { invokePackProtableRequest } from '../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../api/request';
import { useClusterGlobalContext } from '../../../context/clusterGlobalContext';
import type { ProColumns } from '@ant-design/pro-components';
import { useParams } from 'react-router-dom';
import { memo } from 'react';


const showFormModal = () =>
    import("./BuildOrEditModal/api");


const Index = () => {

    const { clusterId } = useParams()

    const onBuildOrEditClick = async ({
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
    }

    const columns: ProColumns[] = [
        {
            dataIndex: 'index',
            title: '序号',
            valueType: 'indexBorder',
            width: 48,
        },
        {
            title: '机架名称',
            dataIndex: 'rack',
            ellipsis: true,
        },

        {
            title: '操作',
            valueType: 'option',
            key: 'option',
            width: 200,
            render: invokeGenOptionCol([
                {
                    title: '删除',
                    titleKey: 'username',
                    onClick: async (text, record) => {
                        const params = {
                            rackId: record.id,
                        };
                        return axiosPost(API.deleteClusterRack, params)
                    }
                },
            ])
        },
    ];


    return (
        <CommonTable
            tableProps={{
                request: invokePackProtableRequest(
                    {
                        api: API.getRackList,
                        params: {
                            clusterId
                        }
                    }
                ),
                columns,
                onBuildClick: onBuildOrEditClick,
                search: false,
            }}
        />
    )
};

export default memo(Index)