import { ProCard, ProFormItem, ProFormText, ProFormUploadButton } from "@ant-design/pro-components"
import { invokeGenerateElId, requireRules } from "../../../utils/util";
import { Button, Progress, Tabs, message } from "antd";
import { DownloadOutlined } from "@ant-design/icons";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { invokeMakePartUploadRequest, invokeQueryMergeProgress } from "../../../utils/uploadUtils";
import { axiosJsonPost } from "../../../api/request";
import { API } from "../../../api";

const Index = (props: { current?: number; formMapRef: React.RefObject<any[]>; setSubmitPending: (val: boolean) => void }) => {


    const {
        formMapRef,
        setSubmitPending,
        current
    } = props

    const pendingIdRef = useRef<string>()


    const currentFormRef = formMapRef.current[current]?.current

    const invokeQueryMergeProgressTimeoutRef = useRef<ReturnType<typeof setTimeout>>()

    const controllerRef = useRef<AbortController>()

    const [invokeQueryMergeProgressRes, setInvokeQueryMergeProgressRes] = useState<any>()


    // 下载模式相关状态
    const [mode, setMode] = useState<string>('upload')
    const [downloadUrl, setDownloadUrl] = useState('')
    const [downloadFileName, setDownloadFileName] = useState('')
    const [downloadProgressRes, setDownloadProgressRes] = useState<any>(null)
    const [downloadLoading, setDownloadLoading] = useState(false)
    const [downloadTaskId, setDownloadTaskId] = useState<string | null>(null)
    const [downloadSucceeded, setDownloadSucceeded] = useState(false)

    const downloadProgressTimeoutRef = useRef<ReturnType<typeof setTimeout>>()

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


    const downloadMemoStatus = useMemo(() => {
        const status = downloadProgressRes?.state
        let res
        if (status !== undefined) {
            if ([0, 2].includes(status)) {
                res = 'active'
            } else if (status === -1) {
                res = 'exception'
            } else if (status === 1) {
                res = 'success'
            }
        }
        return res
    }, [downloadProgressRes?.state])

    const downloadMemoPercent = useMemo(() => {
        const { total, downloaded } = downloadProgressRes || {}
        if (total && downloaded !== undefined) {
            return parseFloat(((downloaded / total) * 100).toFixed(2))
        }
        return 0
    }, [downloadProgressRes])


    // 判断是否正在上传/下载中
    const isUploadingOrDownloading = useMemo(() => {
        // 上传模式：检查合并进度
        if (mode === 'upload' && invokeQueryMergeProgressRes) {
            const state = invokeQueryMergeProgressRes.state
            if ([0, 2].includes(state)) {
                return true
            }
        }
        // 下载模式：检查下载状态
        if (mode === 'download' && downloadLoading) {
            return true
        }
        return false
    }, [mode, invokeQueryMergeProgressRes, downloadLoading])

    const handleTabChange = useCallback((key: string) => {
        if (isUploadingOrDownloading) {
            message.warning('当前有任务正在进行中，请等待完成后切换')
            return
        }
        // 切换到下载模式时重置状态
        if (key === 'download') {
            setDownloadUrl('')
            setDownloadFileName('')
            setDownloadProgressRes(null)
            setDownloadLoading(false)
            setDownloadTaskId(null)
            setDownloadSucceeded(false)
        }
        setMode(key)
    }, [isUploadingOrDownloading])


    const invokeCancelQueryMergeProgressTimeoutRef = useCallback(() => {
        if (invokeQueryMergeProgressTimeoutRef.current) {
            clearTimeout(invokeQueryMergeProgressTimeoutRef.current)
            invokeQueryMergeProgressTimeoutRef.current = undefined
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


    // 下载进度轮询
    const invokeCancelDownloadProgressTimeoutRef = useCallback(() => {
        if (downloadProgressTimeoutRef.current) {
            clearTimeout(downloadProgressTimeoutRef.current)
            downloadProgressTimeoutRef.current = undefined
        }
    }, [])

    const invokeQueryDownloadProgressProxy = useCallback((taskId) => {

        invokeCancelDownloadProgressTimeoutRef()
        downloadProgressTimeoutRef.current = setTimeout(async () => {
            const res = await axiosJsonPost(
                API.queryDownloadProgress,
                { id: taskId }
            )

            if (res.code === 200) {
                setDownloadProgressRes(res.data)

                // 继续轮询：初始化(0)、下载中(2)
                if ([0, 2].includes(res.data.state)) {
                    invokeQueryDownloadProgressProxy(taskId)
                } else {
                    // 完成/失败/取消，停止轮询
                    setDownloadLoading(false)
                    setSubmitPending(false)

                    if (res.data.state === 1 && res.data.attachId) {

                        currentFormRef?.setFieldsValue({
                            attachId: res.data.attachId
                        })
                        message.success('下载成功')
                        setDownloadSucceeded(true)
                    } else if (res.data.state === -1) {
                        // 下载失败
                        const errorMsg = res.data.error || '下载失败，请重试'
                        message.error(errorMsg)
                    }
                }
            } else {
                setDownloadLoading(false)
                setSubmitPending(false)
            }

        }, 1 * 1000)
    }, [invokeCancelDownloadProgressTimeoutRef, setSubmitPending, currentFormRef, downloadFileName, formMapRef, setDownloadSucceeded])

    // 触发下载
    const handleDownload = useCallback(async () => {
        if (!downloadUrl) {
            message.warning('请输入外部部署包地址')
            return
        }
        if (!downloadFileName) {
            message.warning('请输入文件名')
            return
        }


        currentFormRef?.setFieldsValue({
            attachId: ''
        })

        setDownloadLoading(true)
        setSubmitPending(true)
        setDownloadProgressRes(null)

        const res = await axiosJsonPost(
            API.downloadFromUrl,
            {
                url: downloadUrl,
                fileName: downloadFileName
            }
        )

        if (res.code === 200) {
            const taskId = res.data.taskId
            setDownloadTaskId(taskId)
            setDownloadProgressRes(res.data)

            // 开始轮询
            if ([0, 2].includes(res.data.state)) {
                invokeQueryDownloadProgressProxy(taskId)
            }
        } else {
            setDownloadLoading(false)
            setSubmitPending(false)
            message.error(res.msg || '下载启动失败')
        }
    }, [downloadUrl, downloadFileName, currentFormRef, setSubmitPending, invokeQueryDownloadProgressProxy])

    // 取消下载
    const handleCancelDownload = useCallback(async () => {
        if (!downloadTaskId) return

        const res = await axiosJsonPost(
            API.cancelDownload,
            { id: downloadTaskId }
        )

        if (res.code === 200) {
            invokeCancelDownloadProgressTimeoutRef()
            setDownloadLoading(false)
            setDownloadProgressRes(null)
            setDownloadTaskId(null)
            setSubmitPending(false)
            message.info('已取消下载')
        }
    }, [downloadTaskId, invokeCancelDownloadProgressTimeoutRef, setSubmitPending])

    // 清理下载轮询
    useEffect(() => {
        return () => {
            invokeCancelDownloadProgressTimeoutRef()
        }
    }, [invokeCancelDownloadProgressTimeoutRef])

    useEffect(() => {
        return () => {
            invokeCancelQueryMergeProgressTimeoutRef()
        }
    }, [invokeCancelQueryMergeProgressTimeoutRef])

    const uploadTab = (
        <ProFormUploadButton
            label="部署包"
            name="pkgFileId"
            rules={requireRules}
            max={1}
            title={'选择并上传部署文件'}
            listType="text"
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

    )

    const downloadTab = (
        <div>
            <ProFormText
                label="外部地址"
                name="downloadUrl"
                placeholder="文件 URL，支持scp://和 http/https://, eg: scp://username:password@host:port/path/to/file"
                rules={requireRules}
                fieldProps={{
                    value: downloadUrl,
                    onChange: (e) => setDownloadUrl(e.target.value)
                }}
            />
            <ProFormText
                label="文件名"
                name="downloadFileName"
                placeholder="输入文件名"
                rules={requireRules}
                fieldProps={{
                    value: downloadFileName,
                    onChange: (e) => setDownloadFileName(e.target.value)
                }}
            />
            {
                downloadProgressRes && (
                    <ProFormItem
                        label="下载进度"
                    >
                        <Progress
                            percent={downloadMemoPercent}
                            status={downloadMemoStatus}
                            size="small"
                        />
                    </ProFormItem>
                )
            }
            <ProFormItem name="attachId" label="">
                <Button
                    type={!downloadLoading && 'primary'}
                    icon={!downloadLoading && <DownloadOutlined />}
                    disabled={!downloadFileName}
                    onClick={downloadLoading ? handleCancelDownload : handleDownload}
                >
                    {downloadLoading ? '取消' : (downloadSucceeded ? '重新下载' : '下载')}
                </Button>

            </ProFormItem>

        </div>
    )

    return (
        <ProCard bordered={true} className="!mb-[20px]">
            <Tabs
                activeKey={mode}
                onChange={handleTabChange}
                items={[
                    {
                        key: 'upload',
                        label: '本地上传',
                        children: uploadTab
                    },
                    {
                        key: 'download',
                        label: '外部地址',
                        children: downloadTab
                    }
                ]}
            />

            {
                mode === 'upload' && invokeQueryMergeProgressRes &&
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