import { forwardRef, useCallback, useImperativeHandle } from "react";
import Step3 from "../../../../../../../components/UploadDeployConfigModal/Step3";
import { API } from "../../../../../../../api";
import { axiosJsonPost } from "../../../../../../../api/request";

const Index = (props, ref) => {


    const {
        formMapRef,
        index,
    } = props


    const invokeValid = useCallback(async () => {


        const validateFieldsRes = await formMapRef.current[index]?.current?.getFieldsValue()


        let res = {
            valid: true
        }
        const data = validateFieldsRes.deployFileId?.[0]?.response?.data

        if (data) {

            const validMetaFileRes = await axiosJsonPost(API.validMetaFile, {
                meteFileId: data.id,
                contentDecodePasswd: validateFieldsRes.contentDecodePasswd
            })

            let msg = ''
            if (validMetaFileRes.code === 200) {
                msg = validMetaFileRes.data?.errors?.join(',')
            } else {
                msg = validMetaFileRes.msg
            }


            if (msg) {
                msg = `校验失败：${msg}`
            }

            res = {
                valid: !msg,
                data,
                msg
            }
        }


        return res

    }, [formMapRef, index])


    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })


    return (
        <>
            <Step3 />
        </>
    )
}


export default forwardRef(Index) 
