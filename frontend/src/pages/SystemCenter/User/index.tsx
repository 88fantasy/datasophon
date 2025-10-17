
import { API } from '../../../api';
import { invokePackProtableRequest } from '../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../api/request';
import { useClusterGlobalContext } from '../../../context/clusterGlobalContext';
import type { ProColumns } from '@ant-design/pro-components';


const showFormModal = () =>
    import("./BuildOrEditModal/api");


const Index = () => {

    const { clusterId } = useClusterGlobalContext()


    console.log('clusterId', clusterId)
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
            title: '标签名称',
            dataIndex: 'nodeLabel',
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
                            nodeLabelId: record.id,
                        };
                        return axiosPost(API.deleteLabel, params)
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
                        api: API.getLabelList,
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

export default Index