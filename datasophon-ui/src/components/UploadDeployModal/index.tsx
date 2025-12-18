
import { StepsForm } from "@ant-design/pro-components";
import axios from "axios"
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Step1 from "./Step1";
import { Button, message } from "antd";
import Step2 from "./Step2";
import UploadStep from "./UploadStep";
import Step4 from "./Step4";
import { axiosJsonPost } from "../../api/request";
import { API } from "../../api";
import { showMsgAfferRequest } from "../../utils/util";


const Index = (props) => {

    // const [fileList, setFileList] = useState<UploadFile[]>([]);

    const {
        invokeInjectConfirmEvent,
        clusterId,
        onCancelClickProxy,
        record
    } = props

    // const formRef = useRef()

    const formMapRef = useRef([])

    const [currentStep, setCurrentStep] = useState(0)



    // console.log('props', props,)


    const invokeRenderSteps = () => {
        const arr = [
            // {
            //     title: '上传配置文件',
            //     render: Step1
            // },
            // {
            //     title: '上传部署包',
            //     render: Step2
            // },
            // {
            //     title: '导入安装组件',
            //     render: Step1
            // },
            // {
            //     title: '导入安装组件',
            //     render: <Step4/>
            // },
            {
                title: '上传文件',
                render: UploadStep
            },
            {
                title: '导入安装组件',
                render: currentStep === 1 && <Step4
                    key={currentStep}
                    formMapRef={formMapRef}
                // formRef={formRef}
                />
            },
        ]

        // if (stepsType === T_STEPS_TYPE_HOSTMANAGE) {
        //     arr = arr.slice(0, 3)
        // }

        return arr.map(val => {
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
    }

    const stepsDom = invokeRenderSteps()


    const onCurrentChange = useCallback((e) => {
        console.log('e', e)
        setCurrentStep(e)
    }, [])


    const submitter = useMemo(() => {
        return {
            render: (props) => {
                console.log('props', props)

                const {
                    form,
                    step,
                    onSubmit,
                    onPre
                } = props


                if (step === 1) {
                    const nextClick = () => {
                        // onSubmit()
                        const values = form?.getFieldsValue() || {}


                        if (!values.importCmp) {
                            message.error('导入未完成,请稍后重试')
                        } else {
                            onSubmit()
                        }
                    }


                    return (
                        <Button
                            type="primary"
                            onClick={nextClick}
                        // disabled={!meteFileId}
                        >
                            开始部署
                        </Button>
                    )
                }

                return (
                    <Button
                        type="primary"
                        onClick={() => onSubmit()}
                    >
                        下一步
                    </Button>
                )
            }
        }
    }, [])

    const onFinish = useCallback(async (valuse) => {
        const res = await axiosJsonPost(
            API.deploy,
            {
                clusterId: record.id,
                deployFileId: valuse.deployFileId[0]?.response?.data.id,
                contentDecodePasswd: valuse.contentDecodePasswd
            }
        )

        showMsgAfferRequest(res)
        if (res.code === 200) {
            onCancelClickProxy()
        }


    }, [record, onCancelClickProxy])



    return (
        <>
            <StepsForm
                formMapRef={formMapRef}
                onFinish={onFinish}
                formProps={{
                    className: 'w-[30vw]'
                }}
                submitter={submitter}
                onCurrentChange={onCurrentChange}

            >
                {stepsDom}
            </StepsForm>
        </>

    )
}




export default Index