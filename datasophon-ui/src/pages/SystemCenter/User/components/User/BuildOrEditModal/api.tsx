/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormSelect, ProFormText } from "@ant-design/pro-components";
import { requireRules } from "../../../../../../utils/util";
import { API } from "../../../../../../api";
import { axiosPost } from "../../../../../../api/request";



const showFormModal = () =>
  import("../../../../../../components/Common/CommonModal/FormModal/api");

export default async function (config) {
  const [modelApi, clusterUserCreateRes] = await Promise.all([
    showFormModal(),
    axiosPost(API.clusterGroupList, {
      pageSize: 1000,
      page: 1,
      groupName: "",
      clusterId: config.clusterId
    })
  ])


  if (clusterUserCreateRes.code === 200) {

    const options = clusterUserCreateRes.data.map(item => {
      return {
        label: item.groupName,
        value: item.id,
        originData: item
      }
    })
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
          title: '主用户组',
          dataIndex: 'mainGroupId',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options
          },
        },
        {
          title: '附属用户组',
          dataIndex: 'usergroup',
          com: ProFormSelect,
          formItemProps: {
            mode: 'multiple',
            options
          },
        },
      ],
      apiConfig: {
        add: (params) => {
          const body = {
            clusterId: config.clusterId,
            username: params.username,
            mainGroupId: params.mainGroupId,
            otherGroupIds: !params.usergroup ? "" : params.usergroup.join(',')
          }
          return axiosPost(API.clusterUserCreate, body)
        }
      },
      ...config
    })
  }


}
