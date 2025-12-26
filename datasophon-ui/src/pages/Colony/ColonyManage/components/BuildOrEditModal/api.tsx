/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormSelect, ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../../utils/util";
import { axiosPost } from "../../../../../api/request";
import { API } from "../../../../../api";
import asyncHook from "../../../../../components/Common/CommonModal/asyncHook";


const showFormModal = asyncHook(() =>
  import("../../../../../components/Common/CommonModal/FormModal/api"));


export default async function (config) {
  const [
    getFrameListRes,
    modelApi
  ] = await Promise.all([
    axiosPost(API.getFrameList, {}),
    showFormModal()
  ])


  if (getFrameListRes.code === 200) {

    modelApi.default({
      columns: [
        {
          title: '集群名称',
          dataIndex: 'clusterName',
          com: ProFormText,
          formItemProps: {
            rules: requireRules
          },
        },
        {
          title: '集群编码',
          dataIndex: 'clusterCode',
          com: ProFormText,
          formItemProps: {
            rules: requireRules
          },
        },
        {
          title: '集群框架',
          dataIndex: 'clusterFrame',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options: getFrameListRes.data.map(item => {
              return {
                label: item.frameCode,
                value: item.frameCode
              }
            })
          },
        },
      ],
      apiConfig: {
        add: API.saveColony,
        update: API.updateColony
      },
      ...config
    })
  }

}
