/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormSelect, ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../utils/util";
import { API } from "../../../api";
import { axiosPost } from "../../../api/request";



const showFormModal = () =>
  import("../../../components/Common/CommonModal/FormModal/api");

export default async function (config) {
  const [modelApi, getRackListRes] = await Promise.all([
    showFormModal(),
    axiosPost(API.getLabelList, {
      clusterId: config.clusterId
    })
  ])


  if (getRackListRes.code === 200) {

    const options = getRackListRes.data.map(item => {
      return {
        label: item.nodeLabel,
        value: item.id,
        originData: item
      }
    })

    modelApi.default({
      title: '分配标签',
      columns: [
        {
          title: '标签',
          dataIndex: 'nodeLabelId',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options
          },
        },

      ],
      apiConfig: {
        add: (params) => {


          params.clusterId = config.clusterId
          params.hostIds = config.hostIds

          return axiosPost(API.assginLabel, params)
        },
      },
      ...config
    })
  }
}
