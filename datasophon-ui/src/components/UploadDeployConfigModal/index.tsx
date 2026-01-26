
import { ProForm } from "@ant-design/pro-components";
import Step3 from "./Step3";
import { useCallback, useEffect, useRef } from "react";
import { axiosJsonPost } from "../../api/request";
import { API } from "../../api";
import { requireRules, showMsgAfferRequest } from "../../utils/util";
import { invokeGenPath } from "../../utils/routerUtils";


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