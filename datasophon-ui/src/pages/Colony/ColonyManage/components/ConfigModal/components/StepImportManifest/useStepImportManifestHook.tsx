import { useCallback } from "react"

export const useStepImportManifestHook = (obj) => {


    const {
        formMapRef
    } = obj

    const invokeGetManifestData = useCallback(() => {

        const stepImportManifestRef = formMapRef.current[0]

        const values = stepImportManifestRef?.current?.getFieldsValue() || {}
        const deployFileId = values.deployFileId
        const contentDecodePasswd = values.contentDecodePasswd
        if (deployFileId?.length) {

            return {
                data: deployFileId?.[0]?.response.data,
                contentDecodePasswd
            }
        }
    }, [formMapRef])


    return {
        invokeGetManifestData
    }
}