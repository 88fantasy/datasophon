
import { ProForm } from "@ant-design/pro-components";
import Step3 from "./Step3";
import { useCallback, useEffect, useRef } from "react";
import { axiosJsonPost } from "../../api/request";
import { API } from "../../api";
import { requireRules, showMsgAfferRequest } from "../../utils/util";
import { invokeGenPath } from "../../utils/routerUtils";
import { message } from "antd";


const Index = (props) => {

    const {
        invokeInjectConfirmEvent,
        record
    } = props

    const formRef = useRef()

    const onComfirm = useCallback(async () => {
        try {
            const validateFieldsRes = await formRef.current.validateFields()

            if (validateFieldsRes) {

                const body = {
                    clusterId: record.id,
                    deployFileId: validateFieldsRes.deployFileId[0]?.response?.data.id,
                    contentDecodePasswd: validateFieldsRes.contentDecodePasswd || ''
                }


                const validMetaFileRes = await axiosJsonPost(API.validDeploymentFile, body)

                let msg = ''
                if (validMetaFileRes.code === 200) {
                    msg = validMetaFileRes.data?.errors?.join(',')
                } else {
                    msg = validMetaFileRes.msg
                }


                if (msg) {
                    msg = `校验失败：${msg}`
                }


                if (msg) {
                    message.warning(msg)
                    return false
                }


                const res = await axiosJsonPost(
                    API.deploy,
                    {
                        clusterId: record.id,
                        deployFileId: validateFieldsRes.deployFileId[0]?.response?.data.id,
                        contentDecodePasswd: validateFieldsRes.contentDecodePasswd
                    }
                )

                showMsgAfferRequest(res)
                if (res.code === 200) {
                    window.open(invokeGenPath(`/ddh/Dag?dagId=${res.data?.dagId || ''}`))

                    return res
                } else {
                    return false
                }
            }

        } catch (error) {
            return false
        }
    }, [record.id])


    useEffect(() => {
        invokeInjectConfirmEvent(onComfirm)
    }, [invokeInjectConfirmEvent, onComfirm])
    return (
        <ProForm
            formRef={formRef}
            submitter={false}
        >
            <Step3

                requireRules={requireRules}
            />

        </ProForm>

    )
}




export default Index