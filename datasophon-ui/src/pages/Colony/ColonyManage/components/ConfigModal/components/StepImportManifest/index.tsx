import { forwardRef, useCallback, useImperativeHandle } from "react";
import Step3 from "../../../../../../../components/UploadDeployConfigModal/Step3";

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
            res = {
                valid: true,
                data
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
