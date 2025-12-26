/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormSelect, ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../../utils/util";
import { API } from "../../../../../api";
import { axiosPost } from "../../../../../api/request";
import asyncHook from "../../../../../components/Common/CommonModal/asyncHook";



const showFormModal = asyncHook(() =>
    import("../../../../../components/Common/CommonModal/FormModal/api"));

export default async function (config) {
    const [modelApi, getRoleGroupListRes] = await Promise.all([
        showFormModal(),
        axiosPost(API.getRoleGroupList, {
            serviceInstanceId: config.serviceInstanceId
        })
    ])

    if (getRoleGroupListRes.code === 200) {
        const options = getRoleGroupListRes.data.map(val => {
            return {
                key: val.id,
                label: val.roleGroupName
            }
        })
        modelApi.default({
            columns: [
                {
                    title: '角色组名称',
                    dataIndex: 'roleGroupId',
                    com: ProFormSelect,
                    formItemProps: {
                        rules: requireRules,
                        options
                    },
                },
            ],
            apiConfig: {
                add: (params) => {
                    params.roleInstanceIds = config.roleInstanceIds.join()
                    return axiosPost(API.editRoleGroupBind, params)
                }
            },
            ...config
        })
    }


}
