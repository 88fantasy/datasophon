
import { API } from '../../../api';
import { invokePackProtableRequest } from '../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../api/request';
import asyncHook from '../../../components/Common/CommonModal/asyncHook';


const showFormModal = asyncHook(() =>
    import("./BuildOrEditModal/api"));

const onBuildOrEditClick = async ({
    action,
    record
}) => {
    const modelApi = await showFormModal()

    modelApi.default({
        record,
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
        title: '用户名',
        dataIndex: 'username',
        ellipsis: true,
    },
    {
        title: '邮箱',
        dataIndex: 'email',
        search: false,
        ellipsis: true,
    },
    {
        title: '电话',
        dataIndex: 'phone',
        ellipsis: true,
        search: false,
    },
    {
        title: '创建时间',
        dataIndex: 'createTime',
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
                title: '编辑',
                onClick: async (text, record, _, action) => {
                    return onBuildOrEditClick({
                        record,
                        action
                    })
                }
            },
            {
                disabled: (text, record) => {
                    return record.userType === 1
                },
                title: '删除',
                titleKey: 'username',
                onClick: async (text, record) => {
                    const params = JSON.stringify([record.id])

                    return axiosPostUpload(API.deleteUser, params)
                }
            },
        ])
    },
];

const Index = () => {

    return (
        <CommonTable
            tableProps={{
                request: invokePackProtableRequest(API.getUserList),
                columns,
                onBuildClick: onBuildOrEditClick,
            }}
        />
    )
};

export default Index