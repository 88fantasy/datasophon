
import { StepsForm } from "@ant-design/pro-components";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Step1 from "./Step1";
import { Button, message } from "antd";
import Step2 from "./Step2";
import Step4 from "./Step4";
import { axiosJsonPost } from "../../api/request";
import { API } from "../../api";
import { showMsgAfferRequest } from "../../utils/util";
import { invokeGenPath } from "../../utils/routerUtils";
import styles from './index.module.less'


const Index = (props) => {

    // const [fileList, setFileList] = useState<UploadFile[]>([]);

    const {
        invokeInjectConfirmEvent,
        clusterId,
        onCancelClickProxy,
        record,
        type
    } = props

    // const formRef = useRef()

    const formMapRef = useRef([])
    const step4Ref = useRef()

    const [currentStep, setCurrentStep] = useState(0)
    const [submitPending, setSubmitPending] = useState(false)



    // console.log('props', props,)

    const memoArr = useMemo(() => {
        const arr = [
            {
                title: '上传配置文件',
                render: Step1
            },
            {
                title: '上传部署包',
                render: Step2
            },
            {
                title: '导入安装组件',
                render: Step4,
                ref: step4Ref
            },
        ].filter(Boolean)


        return arr.map((val, index) => {


            const Com = val.render


            val = {
                ...val,
                render: (
                    <Com
                        {
                        ...val
                        }
                        // record={record}
                        current={currentStep}
                        formMapRef={formMapRef}
                        // indexKey={'11'}
                        setCurrentStep={setCurrentStep}
                        setSubmitPending={setSubmitPending}
                        type={type}
                        index={index}
                        key={index}
                    />
                )
            }
            return val

        })
    }, [currentStep, type])

    const invokeRenderSteps = useCallback(() => {

        return memoArr.map(val => {
            return (
                <StepsForm.StepForm
                    name={val.title}
                    title={val.title}
                    key={val.title}
                // className="mb-[20px]"

                >
                    {
                        typeof val.render === 'function' ? val.render?.() : val.render
                    }
                </StepsForm.StepForm>
            )
        })
    }, [memoArr])

    const stepsDom = invokeRenderSteps()


    const onCurrentChange = useCallback((e) => {
        console.log('e', e)
        setCurrentStep(e)
    }, [])


    const submitter = useMemo(() => {
        return {
            render: (props, dom) => {
                console.log('props', props)

                const {
                    form,
                    step,
                    onSubmit,
                    onPre
                } = props


                const onSubmitProxy = async () => {

                    const values = form.getFieldsValue()

                    if (values.pkgFileId?.[0].status === 'uploading') {
                        return message.warning('请等待上传完成')
                    }

                    setSubmitPending(true)
                    await onSubmit()

                    setSubmitPending(false)
                }


                if (step === 2) {
                    return
                }

                return (
                    <>
                        {
                            !!step && <Button
                                onClick={onPre}
                                disabled={submitPending}
                            >
                                上一步
                            </Button>
                        }
                        <Button
                            type="primary"
                            onClick={onSubmitProxy}
                            loading={submitPending}
                            disabled={submitPending}
                        >
                            下一步
                        </Button>
                    </>
                )
            }
        }
    }, [submitPending])

    const onFinish = useCallback(async (valuse) => {
        // const res = await axiosJsonPost(
        //     API.deploy,
        //     {
        //         clusterId: record.id,
        //         deployFileId: valuse.deployFileId[0]?.response?.data.id,
        //         contentDecodePasswd: valuse.contentDecodePasswd
        //     }
        // )

        // showMsgAfferRequest(res)
        // if (res.code === 200) {
        //     window.open(invokeGenPath(`/ddh/Dag?dagId=${res.data?.dagId || ''}`))
        //     onCancelClickProxy()
        // }


    }, [])



    return (
        <>
            <StepsForm
                formMapRef={formMapRef}
                onFinish={onFinish}
                formProps={{
                    className: `w-[30vw] overflow-hidden`
                }}
                submitter={submitter}
                onCurrentChange={onCurrentChange}
                current={currentStep}


            >
                {stepsDom}
            </StepsForm>
        </>

    )
}




export default Index