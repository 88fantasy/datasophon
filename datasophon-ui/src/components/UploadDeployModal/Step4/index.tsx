import { Button, message, Progress, Tooltip, type ProgressProps } from "antd"
import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react"
import { axiosJsonPost } from "../../../api/request";
import { API } from "../../../api";
import { ProFormItem } from "@ant-design/pro-components";
import { QuestionCircleOutlined } from "@ant-design/icons";


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
    3: '解压安装包',
    4: '保存数据',
    5: '上传安装包到nexus',
    6: '上传镜像到nexus',
    7: '上传helm包到nexus',

}

const steps = Object.keys(stateMap).filter(val => Number(val) >= 0).length

const Index = forwardRef((props, ref) => {

    const {
        // formMapRef,
        formMapRef,
        clusterId,
        key,
        record,
        current,
        index,
        setCurrentStep
    } = props

    const invokeUpdateProgressId = useRef()
    const [state, setState] = useState({})
    const [pending, setPending] = useState(false)


    const invokeCancelUpdateProgress = useCallback(() => {
        if (invokeUpdateProgressId.current) {
            clearTimeout(invokeUpdateProgressId.current)
            invokeUpdateProgressId.current = undefined

        }

        setPending(false)

    }, [])


    const invokeUpdateProgress = useCallback((id, delay = 0) => {
        invokeCancelUpdateProgress()

        setPending(true)
        const fn = async () => {
            const res = await axiosJsonPost(
                API.queryProgress,
                {
                    id
                }
            )

            if (res.code === 200) {

                if (res.data.state === 1) {
                    const formRef = formMapRef.current[index]
                    // const values = formRef?.current?.getFieldsValue()
                    formRef.current.setFieldsValue({
                        importCmp: true
                    })
                }



                setState(preState => {
                    res.data.preState = preState.queryProgressRes?.state
                    return {
                        ...preState,
                        queryProgressRes: res.data
                    }
                })
            }

            if ([1, -1, -2].includes(Number(res.data.state))) {
                invokeCancelUpdateProgress()
            } else {
                if (![1, -1].includes(Number(res.data.state))) {
                    setTimeout(() => {
                        invokeUpdateProgress(id)
                    }, 2 * 1000)
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
    }, [formMapRef, index, invokeCancelUpdateProgress])


    const invokeValidFile = useCallback(async ({
        firstFormRefValues,
        secondFormRefValues
    }) => {

        const {
            contentDecodePasswd
        } = firstFormRefValues
        let {
            meteFileId,
        } = firstFormRefValues

        let {
            pkgFileId
        } = secondFormRefValues


        const arr = []


        let res = ''


        meteFileId = meteFileId[0]?.response?.data.id

        pkgFileId = pkgFileId?.[0]?.response?.data?.id


        if (
            meteFileId &&
            contentDecodePasswd
        ) {


            arr.push(
                axiosJsonPost(API.validMetaFile, {
                    meteFileId,
                    contentDecodePasswd
                }),
            )
        }


        if (pkgFileId) {
            arr.push(
                axiosJsonPost(API.validatePkgFile, {
                    pkgFileId,
                    meteFileId,
                    contentDecodePasswd
                })
            )
        }


        const arrRes = await Promise.all(arr)


        arrRes.forEach(val => {
            if (val.code === 200) {
                const msg = val.data?.errors?.join(',')

                if (msg) {
                    res += `${msg},`
                }

            } else {
                res += `${val.msg || ''},`
            }
        })


        res = res.replace(/,$/, '')


        if (res) {

            res = `校验失败：${res}`

            setState(preState => {
                return {
                    ...preState,
                    queryProgressRes: {
                        error: res,
                        state: -1
                    }
                }
            })
        }



        return res


    }, [])


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

        setState(preState => {
            return {
                ...preState,
                queryProgressRes: undefined
            }
        })

        const invokeValidFileRes = await invokeValidFile({
            firstFormRefValues,
            secondFormRefValues
        })
        if (invokeValidFileRes) {
            return
        }


        if (
            meteFileId &&
            // pkgFileId &&
            contentDecodePasswd
        ) {



            const res = await axiosJsonPost(
                API.importCmp,
                {
                    // clusterId,
                    // deployFileId: formRef.current?.getFieldsValue().deployFileId
                    contentDecodePasswd,
                    meteFileId: meteFileId[0]?.response?.data.id,
                    pkgFileId: pkgFileId?.[0]?.response?.data?.id
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

    }, [formMapRef, invokeUpdateProgress, invokeValidFile])


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


    const memoPrecent = useMemo(() => {
        let currentState = state?.queryProgressRes?.state

        if (currentState === 1) {
            return 100
        } else {
            if (currentState === -1) {
                currentState = state?.queryProgressRes?.preState
            }
            return ((currentState || 1) - 1) / steps * 100
        }

    }, [state])

    const format = useCallback((percent) => {
        if (memoStatus.status === 'success') {
            return `成功`
        } else if (memoStatus.status === 'exception') {
            return (
                <div>
                    <Tooltip
                        title={
                            state.queryProgressRes?.error || stateMap[state.queryProgressRes?.state]
                        }
                    >
                        <div className="relative">
                            失败
                            <QuestionCircleOutlined
                                className="absolute !text-slate-400 ml-[10px] !text-[16px]" />
                        </div>

                    </Tooltip>

                </div>
            )
        }

        return (
            <div>
                <div>{percent}%</div>

                {
                    stateMap[state?.queryProgressRes?.state] &&
                    ![1].includes(Number(state?.queryProgressRes?.state)) &&
                    <div className="mb-[20px] text-[14px] mt-[20px]">
                        {/* 当前状态： */}
                        {stateMap[state?.queryProgressRes?.state]}
                        {
                            !!state?.queryProgressRes?.progress &&
                            <span className="ml-[10px] ">
                                {
                                    `${state?.queryProgressRes?.progress ? (state.queryProgressRes.progress * 100).toFixed(2) : '0'}%`
                                }
                            </span>
                        }
                    </div>
                }

            </div>
        )


    }, [memoStatus.status, state.queryProgressRes])

    // useEffect(() => {
    //     // if (current === index) {
    //     //     invokeInit()
    //     // }
    // }, [current, index, invokeInit])


    const onPreClick = useCallback(() => {
        setCurrentStep(current - 1)
    }, [current, setCurrentStep])


    useEffect(() => {
        if (current !== index) {
            invokeCancelUpdateProgress()
        }
    }, [current, index, invokeCancelUpdateProgress])


    useEffect(() => {
        return () => {
            invokeCancelUpdateProgress()
        }
    }, [invokeCancelUpdateProgress])

    useImperativeHandle(ref, () => {
        return {
            invokeInit
        }
    })

    return (
        <div className="m-[auto] text-center">
            <ProFormItem
                name="importCmp"
            >
                <Progress
                    type="dashboard"
                    status={memoStatus.status}
                    percent={memoPrecent}
                    steps={steps}
                    railColor="rgba(0, 0, 0, 0.06)"
                    size={300}
                    format={format}
                />
            </ProFormItem>
            <div >
                <Button
                    onClick={onPreClick}
                >
                    上一步
                </Button>
                <Button
                    type="primary"
                    onClick={invokeInit}
                    className="ml-[10px]"
                    loading={pending}
                    disabled={pending}
                >
                    {
                        pending ? '正在导入...' :
                            /success|exception/.test(memoStatus.status) ? '重新导入' : '开始导入'
                    }

                </Button>
            </div>
        </div>
    )
})


export default memo(Index)
