// Mainly handles the scenarios of creating and editing
import type { ProFormInstance } from '@ant-design/pro-components';
import {
    ProFormDateRangePicker,
    ProFormSelect,
    ProFormText,
    ProFormTextArea,
    StepsForm,
} from '@ant-design/pro-components';
import { Alert, message } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import styles from './index.module.less'
import { requireRules } from '../../../../../utils/util';
import Step1 from './components/Step1';
import Step2 from './components/Step2';
import Step3 from './components/Step3';
import Step4 from './components/Step4';
import Step5 from './components/Step5';

// type FormValue = {
//     jobInfo: {
//         name: string;
//         type: number;
//     };
//     syncTableInfo: {
//         timeRange: [Dayjs, Dayjs];
//         title: string;
//     };
// };
// const formValue: FormValue = {
//     jobInfo: {
//         name: 'normal job',
//         type: 1,
//     },
//     syncTableInfo: {
//         timeRange: [dayjs().subtract(1, 'm'), dayjs()],
//         title: 'example table title',
//     },
// };
// const waitTime = (time: number = 100) => {
//     return new Promise((resolve) => {
//         setTimeout(() => {
//             resolve(formValue);
//         }, time);
//     });
// };

const Index = ({
    record,
    onOkClickProxy,

}) => {
    const formMapRef = useRef<
        React.MutableRefObject<ProFormInstance<any> | undefined>[]
    >([]);

    const steps2Ref = useRef()
    const steps3Ref = useRef()
    const steps4Ref = useRef()



    const [current, setCurrent] = useState(0)


    console.log('record', record)

    // useEffect(() => {
    //     waitTime(1000).then(() => {
    //         // In the editing scenario, you need to use formMapRef to loop through and set formData
    //         formMapRef?.current?.forEach((formInstanceRef) => {
    //             formInstanceRef?.current?.setFieldsValue(formValue);
    //         });
    //     });
    // }, []);



    const invokeRenderSteps = () => {
        const arr = [
            {
                title: '安装主机',
                render: Step1
            },
            {
                title: '主机环境校验',
                render: <Step2
                    ref={steps2Ref}
                    current={current}
                    formMapRef={formMapRef}
                    record={record}
                />
            },
            {
                title: '主机Agent分发',
                render: <Step3
                    ref={steps3Ref}
                    current={current}
                    formMapRef={formMapRef}
                    record={record}
                />
            },
            {
                title: '选择服务',
                render: <Step4
                    ref={steps4Ref}
                    current={current}
                    formMapRef={formMapRef}
                    record={record}
                />
            },
            {
                title: '分配服务Master角色',
                render: <Step5
                    // ref={steps4Ref}
                    current={current}
                    formMapRef={formMapRef}
                    record={record}
                />
            },
            // {
            //     title: '分配服务Worker与Client角色'
            // },
            // {
            //     title: '服务配置'
            // },
            // {
            //     title: '安装并启动服务'
            // }
        ]

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
    const onCurrentChange = async (e) => {

        console.log('onCurrentChange', e)
        let valid = true

        if (e > current || (current === stepsDom.length - 1 && !e)) {
            if (current === 1) {
                const res = await steps2Ref.current.invokeValid()
                valid = res.hostCheckCompleted

                if (!valid) {
                    message.warning('存在为未检验成功的主机')
                }
            } else if (current === 2) {
                const res = await steps3Ref.current.invokeValid()
                valid = res.dispatcherHostAgentCompleted

                if (!valid) {
                    message.warning('存在为未分发完成的主机')
                }
            } else if (current === 3) {
                const res = await steps4Ref.current.invokeValid()
                valid = res.valid

                if (!valid) {
                    message.warning(res.msg || '请至少选择一个服务')
                }
            }
        }



        if (valid) {
            if (Math.abs(current - e) === 1) {
                setCurrent(e)
            } else if (current === stepsDom.length - 1) {
                onOkClickProxy()
            }
        }
    }


    useEffect(() => {

    }, [])


    return (
        <StepsForm
            stepsProps={{
                // direction: 'vertical',
                labelPlacement: 'vertical',
                size: 'small',
                // current,
                // onChange: () => {
                //     console.log('onChange111', '')
                // }
            }}

            current={current}
            onCurrentChange={onCurrentChange}

            formProps={{
                grid: true,
            }}
            containerStyle={{
                width: '90%'
            }}
            formMapRef={formMapRef}
            onFinish={(values) => {
                console.log(values);
                return Promise.resolve(true);
            }}

        >
            {
                stepsDom
            }
        </StepsForm>
    );
};
export default Index;