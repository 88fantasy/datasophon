import { ProCard, ProFormDependency, ProFormItem, ProFormText, ProFormUploadButton } from "@ant-design/pro-components"
import { invokeGenerateElId, requireRules } from "../../../utils/util";
import { Progress, type UploadFile } from "antd";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { API } from "../../../api";
import { axiosJsonPost } from "../../../api/request";
import { noop, set } from "lodash-es";
import ChunkedUploader from "../../../utils/ChunkedUploader";
import { CHUNK_SIZE, computeChunkMD5Base64, computeFileMD5, getUploadedChunks, invokeCreateUploadTask, invokeMakePartUploadRequest, invokeQueryMergeProgress, mergeFile, saveUploadedChunk, uploadChunk } from "../../../utils/uploadUtils";

const Index = (props) => {


    const {
        currentStep,
        formMapRef,
        setSubmitPending
    } = props

    const pendingIdRef = useRef()
    const firstFormRef = formMapRef?.current[0]

    const invokeQueryMergeProgressTimeoutRef = useRef()

    const controllerRef = useRef()

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
            } else {
                setSubmitPending(false)
            }

        }, 1 * 1000)
    }, [invokeCancelQueryMergeProgressTimeoutRef, setSubmitPending])


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
                onChange={(info) => {
                    if (!info.fileList.length) {
                        setSubmitPending(false)
                        controllerRef.current?.abort?.()

                        setInvokeQueryMergeProgressRes(undefined)
                    }
                }}
                fieldProps={{
                    customRequest: (options) => {

                        const pendingId = pendingIdRef.current = invokeGenerateElId()


                        const bakOnSuccess = options.onSuccess


                        options.onSuccess = async (...args) => {

                            if (pendingId !== pendingIdRef.current) {
                                return
                            }

                            const obj = args[0]

                            invokeQueryMergeProgressProxy(obj.attachId)

                            bakOnSuccess(...args)

                        }

                        setSubmitPending(true)

                        controllerRef.current = new AbortController()

                        options.signal = controllerRef.current.signal

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
                            validator(rule, value) {

                                return new Promise<void>(async (resolve, reject) => {
                                    if (value?.length) {
                                        console.log('value', value)
                                        // const status = value[0]?.status;
                                        setSubmitPending(true)
                                        setTimeout(async () => {
                                            value = value[0]
                                            if (value?.response?.code === 200) {
                                                resolve()
                                            } else if (value?.status === 'uploading') {
                                                reject('正在上传中,请稍后重试')
                                            } else {
                                                reject('上传失败,请重新上传后重试')
                                            }
                                        }, 2 * 1000)

                                    } else {
                                        resolve()
                                    }
                                })


                            },
                            validateTrigger: 'onSubmit'
                        },
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