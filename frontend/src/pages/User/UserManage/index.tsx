import type { ActionType, ProColumns } from '@ant-design/pro-components';

import { API } from '../../../api';
import { invokePackProtableRequest } from '../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../api/request';


const showBuildModal = () =>
    import("./EditModal/api");


const columns: ProColumns<GithubIssueItem>[] = [
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
        formItemProps: {
            rules: [
                {
                    required: true,
                    message: 'This field is required',
                },
            ],
        },
    },
    {
        title: '邮箱',
        dataIndex: 'email',
        search: false,
        ellipsis: true,
        formItemProps: {
            rules: [
                {
                    required: true,
                    message: 'This field is required',
                },
            ],
        },
    },
    {
        title: '电话',
        dataIndex: 'phone',
        ellipsis: true,
        search: false,
        formItemProps: {
            rules: [
                {
                    required: true,
                    message: 'This field is required',
                },
            ],
        },
    },
    {
        title: '创建时间',
        dataIndex: 'createTime',
        ellipsis: true,
        search: false,
        formItemProps: {
            rules: [
                {
                    required: true,
                    message: 'This field is required',
                },
            ],
        },
    },
    {
        title: '操作',
        valueType: 'option',
        key: 'option',
        render: invokeGenOptionCol([
            {
                title: '编辑',
                onClick: async (text, record, _, action) => {
                    const modelApi = await showBuildModal()

                    modelApi.default({

                    })
                }
            },
            {
                disabled: (text, record) => {
                    return record.userType === 1
                },
                title: '删除',
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
                columns
            }}
        />
    )
};

export default Index