
import { StepsForm } from "@ant-design/pro-components";
import axios from "axios"
import { useCallback, useEffect, useMemo, useRef } from "react";
import Step1 from "./Step1";
import { Button } from "antd";


const Index = (props) => {

    // const [fileList, setFileList] = useState<UploadFile[]>([]);
    const formRef = useRef()

    const formMapRef = useRef([]);

    const {
        invokeInjectConfirmEvent,

    } = props

    // console.log('props', props,)


    const invokeRenderSteps = () => {
        const arr = [
            {
                title: '上传配置文件',
                render: Step1
            },
            {
                title: '上传部署文件',
                render: Step1
            },
            {
                title: '导入安装组件',
                render: Step1
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

                >
                    {
                        typeof val.render === 'function' ? val.render?.() : val.render
                    }
                </StepsForm.StepForm>
            )
        })
    }

    const stepsDom = invokeRenderSteps()


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

                const values = form?.getFieldsValue() || {}


                const {
                    meteFileId
                } = values

                console.log('values', values)

                if (!step) {
                    return (
                        <Button
                            type="primary"
                            onClick={() => props.onSubmit?.()}
                            disabled={!meteFileId}
                        >
                            下一步
                        </Button>
                    )
                }

                return (
                    <Button >
                        12
                    </Button>
                )
            }
        }
    }, [])

    // const onComfirm = useCallback(async () => {

    // }, [])


    // useEffect(() => {
    //     invokeInjectConfirmEvent(onComfirm)
    // }, [invokeInjectConfirmEvent, onComfirm])

    return (
        <>
            <StepsForm

                formMapRef={formMapRef}
                onFinish={async () => {
                    // await waitTime(1000);
                    // message.success('Submission successful');
                }}
                formProps={{
                    // layout: 'horizontal'
                    // className: 'mt-[20px]'
                    // validateMessages: {
                    //     required: 'This field is required',
                    // },
                }}
                submitter={submitter}
            >
                {stepsDom}
            </StepsForm>
        </>

    )
}




export default Index