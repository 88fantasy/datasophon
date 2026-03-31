// Mainly handles the scenarios of creating and editing
import type { ProFormInstance } from '@ant-design/pro-components';
import {

    StepsForm,
} from '@ant-design/pro-components';
import { Alert, Button, message } from 'antd';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import StepImportManifest from './components/StepImportManifest'
import Step1 from './components/Step1';
import Step2 from './components/Step2';
import Step3 from './components/Step3';
import Step4 from './components/Step4';
import Step5 from './components/Step5';
import Step6 from './components/Step6';
import Step7 from './components/Step7';
import Step8 from './components/Step8';
import { ConfigContext } from './configContext';
import { T_SETPS_TYPE_ADDSERVICE, T_SETPS_TYPE_INSTANCE, T_STEPS_TYPE_HOSTMANAGE } from './stepType';
import { T_K8S } from '../../../../../constants/clusterType';



const Index = (props) => {


    const {
        record,
        onOkClickProxy,
        stepsType,
        steps4Data,
        type,
        memoCluster
    } = props

    const formMapRef = useRef([]);

    const stepImportManifestRef = useRef()
    const steps2Ref = useRef()
    const steps3Ref = useRef()
    const steps4Ref = useRef()
    const steps5Ref = useRef()
    const steps6Ref = useRef()
    const steps7Ref = useRef()
    // const refMapRef = useRef({
    //     stepImportManifestRef,
    //     steps2Ref,
    //     steps3Ref,
    //     steps4Ref,
    //     steps5Ref,
    //     steps6Ref,
    //     steps7Ref,
    // })


    const clusterId = record?.id || props.clusterId

    const [current, setCurrent] = useState(0)


    // console.log('record', record)

    // useEffect(() => {
    //     waitTime(1000).then(() => {
    //         // In the editing scenario, you need to use formMapRef to loop through and set formData
    //         formMapRef?.current?.forEach((formInstanceRef) => {
    //             formInstanceRef?.current?.setFieldsValue(formValue);
    //         });
    //     });
    // }, []);

    const memoArr = useMemo(() => {

        const stepImportManifestObj = {
            title: '上传部署清单',
            ref: stepImportManifestRef,
            render: StepImportManifest,
            clusterId
        }

        const step1Obj = {
            title: '安装主机',
            render: Step1
        }

        const step2Obj = {
            title: '主机环境校验',
            ref: steps2Ref,
            render: Step2
        }

        const step3Obj = {
            title: '主机Agent安装',
            ref: steps3Ref,
            render: Step3
        }

        const step4Obj = {
            title: '选择服务',
            ref: steps4Ref,
            render: Step4
        }

        const step5Obj = {
            title: '分配服务Master角色',
            ref: steps5Ref,
            steps4Data,
            render: Step5
        }
        const step6Obj = {
            title: '分配服务Worker角色',
            ref: steps6Ref,
            steps4Data,
            render: Step6
        }

        const step7Obj = {
            title: '服务配置',
            ref: steps7Ref,
            steps4Data,
            render: Step7
        }

        const step8Obj = {
            title: '安装并启动服务',
            clusterId,
            render: Step8

        }

        let arr = []

        if (memoCluster.archType === T_K8S) {
            arr = [
                stepImportManifestObj,
                step4Obj,
                step7Obj,
                step8Obj
            ]
        } else {
            arr = [
                step1Obj,
                step2Obj,
                step3Obj,
                step4Obj,
                step5Obj,
                step6Obj,
                step7Obj,
                step8Obj
            ]

            if (stepsType === T_STEPS_TYPE_HOSTMANAGE) {
                arr = [
                    step1Obj,
                    step2Obj,
                    step3Obj,
                ]
            } else if (stepsType === T_SETPS_TYPE_INSTANCE) {
                arr = [
                    step5Obj,
                    step6Obj,
                    step7Obj,
                    step8Obj
                ]
            } else if (stepsType === T_SETPS_TYPE_ADDSERVICE) {
                arr = [
                    stepImportManifestObj,
                    step4Obj,
                    step5Obj,
                    step6Obj,
                    step7Obj,
                    step8Obj
                ]
            }
        }



        return arr.map((val, index) => {


            const Com = val.render


            val = {
                ...val,
                render: (
                    <Com
                        {
                        ...val
                        }
                        record={record}
                        current={current}
                        formMapRef={formMapRef}
                        type={type}
                        memoCluster={memoCluster}
                        // indexKey={'11'}
                        index={index}
                        key={index}
                    />
                )
            }
            return val

        })
    }, [clusterId, current, memoCluster, record, steps4Data, stepsType, type])

    const invokeRenderSteps = useCallback(() => {
        return memoArr.map((val, index) => {

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
    }, [memoArr])

    const stepsDom = invokeRenderSteps()

    const onCurrentChange = useCallback(async (e) => {

        console.log('onCurrentChange', e)
        let valid = true

        if (e > current || (current === memoArr.length - 1 && !e)) {

            const arrObj = memoArr[current]

            if (arrObj.ref?.current) {
                const invokeValidRes = await arrObj.ref?.current?.invokeValid()
                valid = invokeValidRes.valid


                if (!valid) {
                    message.warning(invokeValidRes.msg)
                }
            }
        }


        if (valid) {
            if (Math.abs(current - e) === 1) {
                setCurrent(e)
            } else if (current === memoArr.length - 1) {
                onOkClickProxy()
            }
        }
    }, [current, memoArr, onOkClickProxy])


    return (
        <ConfigContext.Provider
            value={{
                clusterId
            }}
        >
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
                    layout: 'horizontal',
                    colon: false,
                    labelCol: {
                        // style: {
                        //     width: '200px'

                        // },
                        className: `${[6, 4].includes(current) ? '!w-[400px] !text-left' : '!w-[200px] '} `
                    }
                }}
                containerStyle={{
                    width: '90%'
                }}
                formMapRef={formMapRef}
                onFinish={(values) => {
                    console.log(values);
                    return Promise.resolve(true);
                }}
                submitter={{
                    render(props, dom) {
                        const {
                            step,
                            onSubmit
                        } = props
                        if (step === stepsDom.length - 1) {
                            return [
                                dom[0],
                                <Button
                                    onClick={onSubmit}
                                    type="primary"
                                    loading={dom[1].props?.loading}
                                    disabled={dom[1].props?.disabled}
                                    key="submit"
                                >
                                    确定
                                </Button>
                            ]
                        } else {
                            return dom
                        }

                    },
                }}

            >
                {
                    stepsDom
                }
            </StepsForm>

        </ConfigContext.Provider>

    );
};
export default Index;