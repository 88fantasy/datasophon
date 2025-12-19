import { Button, Progress, Tooltip, type ProgressProps } from "antd"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { axiosJsonPost } from "../../../api/request";
import { API } from "../../../api";
import { ProFormItem } from "@ant-design/pro-components";


const twoColors: ProgressProps['strokeColor'] = {
    '0%': '#108ee9',
    '100%': '#87d068',
};

const stateMap = {
    0: '初始化',
    1: '成功',
    '-1': '失败',
    '-2': '进度对象不存在',
    2: '解析元数据',
    3: '解压安装包'
}
const Index = (props) => {

    const {
        // formMapRef,
        formMapRef,
        clusterId,
        key,
        record
    } = props

    const invokeUpdateProgressId = useRef()
    const [state, setState] = useState({})


    const invokeCancelUpdateProgress = useCallback(() => {
        if (invokeUpdateProgressId.current) {
            clearTimeout(invokeUpdateProgressId.current)
            invokeUpdateProgressId.current = undefined
        }
    }, [])


    const invokeUpdateProgress = useCallback((id, delay = 0) => {
        invokeCancelUpdateProgress()
        const fn = async () => {
            const res = await axiosJsonPost(
                API.queryProgress,
                {
                    id
                }
            )

            if (res.code === 200) {

                if (res.data.state === 1) {
                    const formRef = formMapRef.current[3]
                    // const values = formRef?.current?.getFieldsValue()
                    formRef.current.setFieldsValue({
                        importCmp: true
                    })
                }

                setState(preState => {
                    return {
                        ...preState,
                        queryProgressRes: res.data
                    }
                })
            }

            if ([1, -1, -2].includes(Number(res.data.state))) {
                invokeCancelUpdateProgress()
            } else {
                if (res.data?.total && res.data?.total !== res.data?.step) {
                    invokeUpdateProgress(id)
                }
            }


        }

        if (!delay) {
            fn()
        } else {
            invokeUpdateProgressId.current = setTimeout(() => {
                fn()
            }, 3 * 1000)
        }
    }, [formMapRef, invokeCancelUpdateProgress])


    const invokeInit = useCallback(async () => {

        console.log('invokeInit', formMapRef)
        const firstFormRef = formMapRef.current[0]
        const secondFormRef = formMapRef.current[1]
        const firstFormRefValues = firstFormRef?.current?.getFieldsValue()
        const secondFormRefValues = secondFormRef?.current?.getFieldsValue()

        const {
            meteFileId,
            contentDecodePasswd
        } = firstFormRefValues

        const {
            pkgFileId
        } = secondFormRefValues

        console.log('invokeInit', meteFileId,
            contentDecodePasswd, pkgFileId, secondFormRefValues)


        if (
            meteFileId &&
            pkgFileId &&
            contentDecodePasswd
        ) {


            const res = await axiosJsonPost(
                API.importCmp,
                {
                    // clusterId,
                    // deployFileId: formRef.current?.getFieldsValue().deployFileId
                    contentDecodePasswd,
                    meteFileId: meteFileId[0]?.response?.data.id,
                    pkgFileId: pkgFileId[0]?.response?.data.id
                }
            )

            if (res.code === 200) {
                invokeUpdateProgress(res.data.progressId)
            } else {
                setState(
                    preState => {
                        return {
                            ...preState,
                            reloadBtnVisiable: true
                        }
                    }
                )
            }
        }

    }, [formMapRef, invokeUpdateProgress])


    const memoStatus = useMemo(() => {
        const res = {}


        res.percent = state.queryProgressRes?.total && (state.queryProgressRes?.step / state.queryProgressRes?.total * 100).toFixed(1)

        const status = Number(state?.queryProgressRes?.state)
        if ([-2, -1].includes(status)) {
            res.status = 'exception'
        } else if ([1].includes(status)) {
            res.status = 'success'
        }


        return res
    }, [state.queryProgressRes?.state, state.queryProgressRes?.step, state.queryProgressRes?.total])

    const format = useCallback((percent) => {
        if (memoStatus.status === 'success') {
            return `成功`
        } else if (memoStatus.status === 'exception' || state.reloadBtnVisiable) {
            return (
                <div>
                    <Tooltip title={state.queryProgressRes?.error}>
                        <div>失败</div>
                    </Tooltip>
                    <Button
                        className="mt-[20px]"
                        variant="filled"
                        onClick={() => {
                            invokeInit()
                        }}
                    >
                        重新导入安装组件
                    </Button>
                </div>
            )
        }

        return (
            <div>
                <div>{percent}%</div>
                <div className="mb-[20px] text-[14px]">
                    {/* 当前状态： */}
                    {stateMap[state?.queryProgressRes?.state]}
                    {
                        !!state?.queryProgressRes?.progress &&
                        <span className="ml-[10px] mt-[10px]">
                            {
                                `${state?.queryProgressRes?.progress}%`
                            }
                        </span>
                    }
                    {/* {
                        !!state.queryProgressRes?.error &&
                        <span className="ml-[10px]">
                          
                        </span>
                    } */}
                </div>
            </div>
        )


    }, [invokeInit, memoStatus.status, state.queryProgressRes?.error, state.queryProgressRes?.progress, state.queryProgressRes?.state, state.reloadBtnVisiable])

    useEffect(() => {
        invokeInit()

    }, [invokeInit,])


    useEffect(() => {
        return () => {
            invokeCancelUpdateProgress()
        }
    }, [invokeCancelUpdateProgress])

    return (
        <div className="m-[auto] text-center">
            <ProFormItem
                name="importCmp"
            >
                <Progress
                    type="circle"
                    status={memoStatus.status}
                    percent={memoStatus.percent}
                    strokeColor={twoColors}
                    size={300}
                    format={format}
                />
            </ProFormItem>
        </div>
    )
}


export default Index