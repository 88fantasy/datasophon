import { ProForm, ProFormSelect, ProFormSwitch, ProFormText, useControlModel } from "@ant-design/pro-components"
import { requireRules, showMsgAfferRequest } from "../../../../../utils/util"
import { Col, Form, Input, Row, Select, Space } from "antd"
import { useCallback, useEffect, useMemo, useRef } from "react"
import { cloneDeep } from "lodash-es"
import { API } from "../../../../../api"
import { axiosJsonPost } from "../../../../../api/request"


const schedulePolicyList = [
    {
        label: "fair",
        value: "fair",
    },
    {
        label: "fifo",
        value: "fifo",
    },
    {
        label: "drf",
        value: "drf",

    },
]

const sourceValidator = (_, value) => {
    if (value.core > 0 && value.mem > 0) {
        return Promise.resolve();
    }
    return Promise.reject('请填写正确的资源数');
}


const SourceDom = (props) => {

    const {

        name,
        label
    } = props

    // console.log('props', props)
    // const model = useControlModel(props, ['core', 'mem']);

    const Dom = (domProps) => {
        const model = useControlModel(domProps, ['core', 'mem']);
        return (
            <div className="flex gap-[10px]">
                <Input {...model.core} min={0} type="number" suffix="Core" />
                <Input {...model.mem} min={0} type="number" suffix="GB" />
            </div>
        )
    }

    return (
        <Form.Item
            name={name}
            label={label}
            rules={
                [
                    {
                        required: true,
                        validator: sourceValidator
                    }
                ]
            }
        >
            <Dom />
        </Form.Item>
    )
}


const Index = (props) => {
    const {
        invokeInjectConfirmEvent,
        record,
        clusterId
    } = props

    const formRef = useRef()

    const onComfirm = useCallback(async () => {
        try {
            const validateFieldsRes = await formRef.current.validateFields()

            if (validateFieldsRes) {
                //  
                // console.log('values', validateFieldsRes)
                // return false
                const values = validateFieldsRes
                const payload = {
                    ...cloneDeep(values),
                    minCore: Number(values.min?.core ?? 0),
                    minMem: Number(values.min?.mem ?? 0),
                    maxCore: Number(values.max?.core ?? 0),
                    maxMem: Number(values.max?.mem ?? 0),
                    allowPreemption: values.allowPreemption ? 1 : 2,
                    clusterId,
                    id: record?.id
                }

                // 删除表单中用于展示的嵌套字段
                delete payload.min
                delete payload.max


                const ajaxApi =
                    payload.id
                        ? API.updateQueue
                        : API.saveQueue;

                const res = await axiosJsonPost(ajaxApi, payload)


                showMsgAfferRequest(res)

                return res.code === 200

            }

        } catch (error) {
            return false
        }
    }, [])

    const memoInitialValue = useMemo(() => {
        if (!record) {
            return record
        }

        const res = cloneDeep(record)
        res.min = {
            core: record.minCore,
            mem: record.minMem
        }
        res.max = {
            core: record.maxCore,
            mem: record.maxMem
        }
        res.allowPreemption = Number(record.allowPreemption) === 1
        return res
    }, [record])

    useEffect(() => {
        invokeInjectConfirmEvent(onComfirm)
    }, [invokeInjectConfirmEvent, onComfirm])


    return (
        <ProForm
            formRef={formRef}
            submitter={false}
            layout="horizontal"
            labelCol={{
                span: 5
            }}
            initialValues={memoInitialValue}
        >
            <ProFormText
                name="queueName"
                label="队列名称"
                formItemProps={{
                    rules: [
                        {
                            required: true,
                            validator: (_, value) => {
                                const reg = /^(?!_)(?!.*?_$)[a-zA-Z0-9_]+$/;
                                if (!value) {
                                    return Promise.reject('请填写名称')
                                }
                                if (!reg.test(value)) {
                                    return Promise.reject('名称只能是数字、字母、下划线且不能以下划线开头和结尾')
                                }
                                return Promise.resolve();
                            }
                        }
                    ]
                }}

            />
            {/* {
                invokeGenSourceDom({
                    name: 'min',
                    label: '最小资源数'
                })
            } */}
            <SourceDom
                name="min"
                label="最小资源数"
            />
            <SourceDom
                name="max"
                label="最大资源数"
            />
            {/* {
                invokeGenSourceDom({
                    name: 'max',
                    label: '最大资源数'
                })
            } */}
            <ProFormText
                name="appNum"
                label="最多同时运行应用数"
                formItemProps={{
                    rules: requireRules
                }}

            />
            <ProFormSelect
                name="schedulePolicy"
                label="资源分配策略"
                options={schedulePolicyList}
                formItemProps={{
                    rules: requireRules,
                }}

            />
            <ProFormText
                name="weight"
                label="权重"
                formItemProps={{
                    rules: requireRules
                }}

            />
            <ProFormText
                name="amShare"
                label="队列中AM占用最大比例"
                formItemProps={{
                    rules: [
                        {
                            required: true,
                            validator: (_, value) => {
                                const reg = /^(([0-9])|([0-9]([0-9]+)))(.[0-9]+)?$/;

                                if (!value) {
                                    return Promise.reject('请输入正数');
                                }

                                if (!reg.test(value) && value) {
                                    return Promise.reject('请输入正数');
                                }
                                if (Number(value) === 0) {
                                    return Promise.reject('请输入正数');
                                }

                                return Promise.resolve();
                            }
                        }
                    ]
                }}

            />
            <ProFormSwitch
                name="allowPreemption"
                label="是否允许队列抢占资源"
            />
        </ProForm>
    )
}


export default Index