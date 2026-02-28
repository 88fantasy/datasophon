import { ProCard, ProFormDependency, ProFormText, ProFormUploadButton } from "@ant-design/pro-components";
import { API } from "../../../api";
import { noop } from "lodash-es";
import TableProxy from "./TableProxy";
import { forwardRef, memo, useCallback, useImperativeHandle, useRef } from "react";
import { invokeMakeCommonProFormUploadButtonCustomRequest } from "../../../utils/uploadUtils";

const Index = (props, ref) => {

    const {
        currentStep,
        formMapRef,
        requireRules
    } = props

    // const tableProxyRef = useRef()



    const invokeRenderTable = useCallback(() => {
        const dom = ({ deployFileId, contentDecodePasswd }) => {
            return (
                <TableProxy
                    // ref={tableProxyRef}
                    deployFileId={deployFileId}
                    contentDecodePasswd={contentDecodePasswd}
                />
            )
        }

        return (
            <ProFormDependency
                name={['deployFileId', 'contentDecodePasswd']}
            >

                {dom}

            </ProFormDependency>
        )
    }, [])


    // const invokeValid = useCallback(() => {

    //     return {
    //         yamlData: tableProxyRef.current?.yamlDataRef?.current,
    //         valid: true
    //     }

    // }, [])


    // useImperativeHandle(ref, () => {
    //     return {
    //         invokeValid
    //     }
    // })

    return (
        <>

            <ProFormUploadButton
                label="部署清单"
                name="deployFileId"
                rules={requireRules}
                max={1}
                listType="text"
                title='选择并上传部署清单'
                action={API.upload}
                fieldProps={{
                    onPreview: noop,
                    customRequest: invokeMakeCommonProFormUploadButtonCustomRequest.bind(noop, API.upload)
                }}
                formItemProps={{
                    rules: [
                        {
                            required: requireRules ? true : false,
                            validator(rule, value) {

                                if (!requireRules) {
                                    return Promise.resolve()
                                }

                                return new Promise<void>((resolve, reject) => {
                                    if (!value?.length) {
                                        reject("请上传部署清单");
                                    } else {
                                        // console.log('value', value)
                                        // const status = value[0]?.status;
                                        setTimeout(async () => {
                                            value = value[0]
                                            if (value?.response?.code === 200) {
                                                resolve()
                                            } else if (value?.status === 'uploading') {
                                                reject('正在上传中,请稍后重试')
                                            } else {
                                                reject('上传失败,请重新上传后重试')
                                            }
                                        }, 1 * 1000)

                                    }
                                })


                            },
                        }
                    ]
                }}
            />
            <ProFormDependency
                name={['deployFileId']}
            >
                {
                    ({ deployFileId }) => (
                        <ProFormText
                            label="配置文件密码"
                            name="contentDecodePasswd"
                            rules={requireRules}
                        />
                    )
                }

            </ProFormDependency>

            {
                invokeRenderTable()
            }
        </>


    )
}



export default memo(Index)