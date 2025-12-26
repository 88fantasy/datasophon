/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormSelect, ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../../utils/util";
import { API } from "../../../../../api";
import { axiosPost } from "../../../../../api/request";
import { cloneDeep } from "lodash-es";
import asyncHook from "../../../../../components/Common/CommonModal/asyncHook";
const showFormModal = asyncHook(() =>
  import("../../../../../components/Common/CommonModal/FormModal/api"));

export default async function (config) {

  const [res, modelApi] = await Promise.all([
    axiosPost(API.queryAllUser, {}),
    showFormModal()
  ])

  if (config.record) {
    config.record = cloneDeep(config.record)
    config.record.userIds = config.record?.clusterManagerList?.map(val => {
      return val.id
    })
  }

  if (res.code === 200) {
    modelApi.default({
      columns: [
        {
          title: '集群管理员',
          dataIndex: 'userIds',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            mode: 'multiple',
            options: res.data.map(item => {
              return {
                label: item.username,
                value: item.id
              }
            })
          },
        },
      ],
      apiConfig: {
        add: (params) => {

          params.clusterId = params.id

          delete params.id

          return axiosPost(API.authCluster, params)
        },
      },
      ...config
    })
  }



}
