// Mainly handles the scenarios of creating and editing
import type { ProFormInstance } from '@ant-design/pro-components';
import {
    ProFormDateRangePicker,
    ProFormSelect,
    ProFormText,
    ProFormTextArea,
    StepsForm,
} from '@ant-design/pro-components';
import { Alert } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import styles from './index.module.less'
import { requireRules } from '../../../../../utils/util';
import Step1 from './components/Step1';
import Step2 from './components/Step2';

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
    record
}) => {
    const formMapRef = useRef<
        React.MutableRefObject<ProFormInstance<any> | undefined>[]
    >([]);

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


    const onCurrentChange = (e) => {
        setCurrent(e)
    }

    const invokeRenderSteps = () => {
        const arr = [
            {
                title: '安装主机',
                render: Step1
            },
            {
                title: '主机环境校验',
                render: <Step2
                    current={current}
                    formMapRef={formMapRef}
                    record={record}
                />
            },
            {
                title: '主机Agent分发'
            },
            {
                title: '选择服务'
            },
            {
                title: '分配服务Master角色'
            },
            {
                title: '分配服务Worker与Client角色'
            },
            {
                title: '服务配置'
            },
            {
                title: '安装并启动服务'
            }
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
                invokeRenderSteps()
            }
        </StepsForm>
    );
};
export default Index;