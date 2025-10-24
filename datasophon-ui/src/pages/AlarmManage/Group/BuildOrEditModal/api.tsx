/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormSelect, ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../utils/util";
import { API } from "../../../../api";
import { axiosJsonPost, axiosPost } from "../../../../api/request";



const showFormModal = () =>
  import("../../../../components/Common/CommonModal/FormModal/api");

export default async function (config) {
  const [modelApi, getAlarmCateRes] = await Promise.all([
    showFormModal(),
    axiosPost(API.getAlarmCate, {
      clusterId: config.clusterId
    })
  ])


  if (getAlarmCateRes.code === 200) {

    const options = getAlarmCateRes.data.map(item => {
      return {
        label: item.serviceName,
        value: item.serviceName,
        originData: item
      }
    })
    modelApi.default({
      columns: [
        {
          title: '告警组名称',
          dataIndex: 'alertGroupName',
          com: ProFormText,
          formItemProps: {
            rules: requireRules
          },
        },
        {
          title: '告警组类别',
          dataIndex: 'alertGroupCategory',
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
          return axiosJsonPost(API.saveGroup, params)
        }
      },
      ...config
    })
  }


}
