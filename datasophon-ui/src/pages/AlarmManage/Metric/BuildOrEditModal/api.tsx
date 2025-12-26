/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";

import { ProFormRadio, ProFormSelect, ProFormText, ProFormTextArea } from "@ant-design/pro-components";
import { requireRules } from "../../../../utils/util";
import { API } from "../../../../api";
import { axiosJsonPost, axiosPost } from "../../../../api/request";
import { use, useRef } from "react";
import asyncHook from "../../../../components/Common/CommonModal/asyncHook";



const showFormModal = asyncHook(() =>
  import("../../../../components/Common/CommonModal/FormModal/api"));



const compareMethodList = [
  {
    label: "!=",
    value: "!=",
  },
  {
    label: ">",
    value: ">",
  },
  {
    label: "<",
    value: "<",
  },
]

const alertTacticList = [
  {
    label: "单次",
    value: 1,
  },
  {
    label: "持续",
    value: 2,
  },
]



const alertLevelList = [
  {
    label: "警告",
    value: "warning",
  },
  {
    label: "异常",
    value: "exception",
  },
]

const noticeList = [
  {
    label: "数据开发组",
    value: 1,
  },
]



export default async function (config) {


  // const formRef = useRef()

  let formRefProxy
  const [modelApi, getAlarmGroupListRes] = await Promise.all([
    showFormModal(),
    axiosPost(API.getAlarmGroupList, {
      pageSize: 1000,
      page: 1,
      clusterId: config.clusterId
    })
  ])



  if (getAlarmGroupListRes.code === 200) {

    const groupList = getAlarmGroupListRes.data.map(val => {
      return {
        label: val.alertGroupName,
        value: val.id
      }
    })

    modelApi.default({
      columns: [
        {
          title: '告警指标名称',
          dataIndex: 'alertQuotaName',
          com: ProFormText,
          formItemProps: {
            rules: requireRules
          },
        },
        {
          title: '指标表达式',
          dataIndex: 'alertExpr',
          com: ProFormText,
          formItemProps: {
            rules: requireRules
          },
        },
        {
          title: '比较方式',
          dataIndex: 'compareMethod',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options: compareMethodList
          },
        },
        {
          title: '告警阀值',
          dataIndex: 'alertThreshold',
          com: ProFormText,
          formItemProps: {
            rules: requireRules,
          },
        },
        {
          title: '告警级别',
          dataIndex: 'alertLevel',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options: alertLevelList
          },
        },
        {
          title: '告警组',
          dataIndex: 'alertGroupId',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options: groupList
          },
        },
        {
          title: '绑定角色',
          dataIndex: 'serviceRoleName',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            dependencies: ['alertGroupId'],
            request: async (e) => {
              const params = {
                alertGroupId: e.alertGroupId,
                clusterId: config.clusterId || "",
              };
              const res = await axiosPost(API.getAlarmRole, params)

              return (res.data || []).map(val => {
                return {
                  value: val.serviceRoleName,
                  label: val.serviceRoleName
                }
              })
            },
          },
        },
        {
          title: '通知组',
          dataIndex: 'noticeGroupId',
          com: ProFormSelect,
          formItemProps: {
            rules: requireRules,
            options: noticeList
          },
        },
        {
          title: '告警策略',
          dataIndex: 'alertTactic',
          com: ProFormRadio.Group,
          formItemProps: {
            rules: requireRules,
            options: alertTacticList
          },
        },
        {
          title: '间隔时长(分钟)',
          dataIndex: 'intervalDuration',
          com: ProFormText,
          formItemProps: {
            rules: requireRules,
          },
        },
        {
          title: '触发时长(秒)',
          dataIndex: 'triggerDuration',
          com: ProFormText,
          formItemProps: {
            rules: requireRules,
          },
        },
        {
          title: '告警建议',
          dataIndex: 'alertAdvice',
          com: ProFormTextArea,
          formItemProps: {
            rules: requireRules,
          },
        },
      ],
      apiConfig: {
        add: (params) => {
          // params.clusterId = config.clusterId


          const api = params.id ? API.updateMetric : API.saveMetric
          return axiosJsonPost(api, params)
        }
      },
      proFormProps: {
        onValuesChange: (values) => {
          if (Object.keys(values).includes('alertGroupId')) {
            formRefProxy?.current?.setFieldsValue({ serviceRoleName: undefined })
          }
        }
      },
      initCallback: ({
        formRef
      }) => {
        formRefProxy = formRef
      },
      ...config
    })
  }


}
