/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../utils/util";
import { API } from "../../../../api";
import { axiosPost } from "../../../../api/request";



const showFormModal = () =>
  import("../../../../components/Common/CommonModal/FormModal/api");

export default async function (config) {
  const modelApi = await showFormModal()

  modelApi.default({
    columns: [
      {
        title: '标签名称',
        dataIndex: 'nodeLabel',
        com: ProFormText,
        formItemProps: {
          rules: requireRules
        },
      },
    ],
    apiConfig: {
      add: (params) => {
        params.clusterId = config.clusterId
        return axiosPost(API.saveLabel, params)
      }
    },
    ...config
  })
}
