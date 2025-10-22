/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../../utils/util";
import { API } from "../../../../../api";



const showFormModal = () =>
  import("../../../../../components/Common/CommonModal/FormModal/api");

export default async function (config) {
  const modelApi = await showFormModal()

  modelApi.default({
    columns: [
      {
        title: '用户名',
        dataIndex: 'username',
        com: ProFormText,
        formItemProps: {
          rules: requireRules
        },
      },
      {
        title: '用户密码',
        dataIndex: 'password',
        com: ProFormText.Password,

        formItemProps: {
          rules: requireRules
        },
      },
      {
        title: '邮箱',
        dataIndex: 'email',
        com: ProFormText,
        formItemProps: {
          rules: requireRules
        },
      },
      {
        title: '电话',
        dataIndex: 'phone',
        com: ProFormText,

        formItemProps: {
          rules: requireRules
        },
      }
    ],
    apiConfig: {
      add: API.addUser,
      update: API.updateUser
    },
    ...config
  })
}
