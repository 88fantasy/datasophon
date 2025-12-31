import { ProCard, ProFormDependency, ProFormItem, ProFormText, ProFormUploadButton } from "@ant-design/pro-components"
import { requireRules } from "../../../utils/util";
import { Progress, type UploadFile } from "antd";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { API } from "../../../api";
import { axiosJsonPost } from "../../../api/request";
import { noop } from "lodash-es";
import ChunkedUploader from "../../../utils/ChunkedUploader";
import { CHUNK_SIZE, computeChunkMD5Base64, computeFileMD5, getUploadedChunks, invokeCreateUploadTask, invokeMakePartUploadRequest, invokeQueryMergeProgress, mergeFile, saveUploadedChunk, uploadChunk } from "../../../utils/uploadUtils";

const Index = (props) => {


    const {
        currentStep,
        formMapRef
    } = props


    const firstFormRef = formMapRef?.current[0]

    const invokeQueryMergeProgressTimeoutRef = useRef()

    const [invokeQueryMergeProgressRes, setInvokeQueryMergeProgressRes] = useState()


    const values = firstFormRef?.current?.getFieldsValue()


    const {
        contentDecodePasswd
    } = values || {}


    let {
        meteFileId
    } = values || {}

    meteFileId = meteFileId?.[0]?.response?.data?.id

    const memoStatus = useMemo(() => {

        const status = invokeQueryMergeProgressRes?.state
        let res
        if (status) {
            if ([0, 2].includes(status)) {
                res = 'active'
            } else if ([0, -1].includes(status)) {
                res = 'exception'
            }

        }

        return res
    }, [invokeQueryMergeProgressRes?.state])


    const invokeCancelQueryMergeProgressTimeoutRef = useCallback(() => {
        if (invokeCancelQueryMergeProgressTimeoutRef.current) {
            clearTimeout(invokeQueryMergeProgressTimeoutRef.current)
            invokeCancelQueryMergeProgressTimeoutRef.current = undefined
        }
    }, [])


    const invokeQueryMergeProgressProxy = useCallback((id) => {

        invokeCancelQueryMergeProgressTimeoutRef()
        invokeQueryMergeProgressTimeoutRef.current = setTimeout(async () => {
            const res = await invokeQueryMergeProgress(id)

            if (res.code === 200) {
                setInvokeQueryMergeProgressRes(res.data)


            }

            if ([0, 2].includes(res.data.state)) {
                invokeQueryMergeProgressProxy(id)
            }

        }, 1 * 1000)
    }, [invokeCancelQueryMergeProgressTimeoutRef])


    useEffect(() => {
        return () => {
            invokeCancelQueryMergeProgressTimeoutRef()
        }
    }, [invokeCancelQueryMergeProgressTimeoutRef])

    return (




        <ProCard bordered={true} className="!mb-[20px]">

            <ProFormUploadButton
                label="部署包"
                name="pkgFileId"
                rules={requireRules}
                max={1}
                title={'选择并上传部署文件'}
                listType="text"
                // action={API.upload}
                // customRequest={async (options: { file: UploadFile }) => {
                //     console.log('options', options)
                // }}
                fieldProps={{
                    customRequest: (options) => {


                        const bakOnSuccess = options.onSuccess


                        options.onSuccess = async (...args) => {

                            const obj = args[0]

                            invokeQueryMergeProgressProxy(obj.attachId)

                            bakOnSuccess(...args)
                        }


                        return invokeMakePartUploadRequest(options)
                    },
                    beforeUpload: async (file) => {
                        invokeCancelQueryMergeProgressTimeoutRef()

                        setInvokeQueryMergeProgressRes(undefined)

                        return true
                    }
                }}
                formItemProps={{
                    rules: [
                        {
                            required: true,
                            validator(rule, value) {

                                return new Promise<void>(async (resolve, reject) => {
                                    if (!value?.length) {
                                        reject();
                                    } else {
                                        console.log('value', value)
                                        // const status = value[0]?.status;
                                        setTimeout(async () => {
                                            value = value[0]
                                            if (value?.response?.code === 200) {
                                                // resolve()
                                                const res = await axiosJsonPost(API.validatePkgFile, {
                                                    pkgFileId: value.response.data.id,
                                                    meteFileId,
                                                    contentDecodePasswd
                                                })
                                                if (
                                                    res.code === 200
                                                ) {
                                                    const msg = res.data.errors?.join(',')

                                                    if (msg) {
                                                        reject(msg)
                                                    } else {
                                                        resolve()
                                                    }
                                                } else {
                                                    TODO:
                                                    // resolve()
                                                    reject(res.msg)
                                                }
                                            } else if (value?.status === 'uploading') {
                                                reject('正在上传中,请稍后重试')
                                            } else {
                                                reject('上传失败,请重新上传后重试')
                                            }
                                        }, 2 * 1000)

                                    }
                                })


                            },
                            validateTrigger: 'onSubmit'
                        },
                        {
                            required: true,
                            validator(rule, value) {
                                return new Promise<void>(async (resolve, reject) => {
                                    if (!value?.length) {
                                        reject("请上传部署包");
                                    } else {
                                        resolve()
                                    }
                                })


                            },
                            validateTrigger: 'onChange'
                        }
                    ]
                }}
            />

            {
                invokeQueryMergeProgressRes &&
                <ProFormItem
                    label="合并进度"
                >
                    <Progress
                        percent={
                            (invokeQueryMergeProgressRes.progress || 0) * 100
                        }
                        status={memoStatus}
                        size="small"
                    />
                </ProFormItem>
            }

        </ProCard>



    )
}


export default memo(Index)