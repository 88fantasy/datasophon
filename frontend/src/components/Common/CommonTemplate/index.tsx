import { grey } from "@ant-design/colors";
import { ProCard, ProFormGroup, ProFormList, ProFormSelect, ProFormSlider, ProFormSwitch, ProFormText } from "@ant-design/pro-components";
import type React from "react";

function invokeMapShowMultiply(item) {
    const inputStringArray =
        item.type === "input" && item.configType === "stringArray";
    return ["multiple"].includes(item.type) || inputStringArray;
}


// const comMap = {
//     input: {
//         com: ProFormText,
//     },
//     password: {
//         com: ProFormText.Password,

//     },
//     slider: {
//         com: ProFormSlider,
//         propsFn: (item) => {
//             return {
//                 min: item.minValue,
//                 max: item.maxValue,
//             }
//         }
//     },
//     switch: ProFormSwitch,
//     select: ProFormSelect
// }


const Index = ({
    templateData,
    className
}) => {



    const invokeRender = () => {

        console.log('templateData', templateData)

        return templateData.map((item, index) => {
            let res


            let label: React.ReactNode = item.label

            item.type = 'multipleWithMap'


            if (
                // item.name !== item.label &&
                item.name
            ) {
                label = (
                    <div>
                        {
                            label
                        }
                        <div
                            style={{
                                color: grey[1]
                            }}
                            className="text-[12px] text-left"
                        >
                            {item.name}
                        </div>
                    </div>
                )
            }


            const commonProps = {
                extra: item.description,
                label,
                placeholder: item.placeholder,
                name: item.name,
                key: item.name
            }


            if (
                !['multipleWithKey', 'multipleWithMap'].includes(
                    item.type
                ) &&
                !invokeMapShowMultiply(item)
            ) {





                if (item.type === 'input') {
                    res = (
                        <ProFormText
                            {...commonProps}
                            rules={[
                                {
                                    required: item.required,
                                    message: `${item.label}不能为空!`,
                                }
                            ]}
                        />
                    )
                } else if (item.type === 'password') {
                    res = (
                        <ProFormText.Password
                            {...commonProps}

                            rules={[
                                {
                                    required: item.required,
                                    message: `${item.label}不能为空!`,
                                }
                            ]}
                        />
                    )
                } else if (item.type === 'slider') {
                    res = (
                        <ProFormSlider
                            {...commonProps}

                            min={item.minValue}
                            max={item.maxValue}

                        />
                    )
                } else if (item.type === 'switch') {
                    res = (
                        <ProFormSwitch
                            {...commonProps}

                        />
                    )
                } else if (item.type === 'select' || item.type === 'multipleSelect') {
                    res = (
                        <ProFormSelect
                            {...commonProps}
                            mode={item.type === 'multipleSelect' ? 'multiple' : 'single'}
                            options={
                                item.selectValue.map(val => {
                                    return {
                                        label: val,
                                        value: val
                                    }
                                })
                            }
                            rules={[
                                {
                                    required: item.required,
                                    message: `${item.label}不能为空!`,
                                }
                            ]}
                        />
                    )
                }



            } else {
                if ('multipleWithKey' === item.type) {
                    res = (
                        <ProFormList
                            {...commonProps}
                            rules={[
                                {
                                    required: item.required,
                                    validator: async (_, value) => {
                                        if (value && value.length > 0) {
                                            return;
                                        }
                                        throw new Error('至少要有一项！');
                                    },
                                },
                            ]}
                        >
                            <ProFormGroup key="group">
                                <ProFormText
                                    colProps={{
                                        span: 12
                                    }}
                                    rules={[
                                        {
                                            validateTrigger: ['change', 'blur'],
                                            required: item.required,
                                            whitespace: true,
                                            message: `${item.label}不能为空!`,
                                        }
                                    ]}
                                    name={`${item.name + 'arrayWithKey'}`}
                                />
                                <ProFormText
                                    colProps={{
                                        span: 12
                                    }}
                                    rules={[
                                        {
                                            validateTrigger: ['change', 'blur'],
                                            required: item.required,
                                            whitespace: true,
                                            message: `不能为空!`,
                                        }
                                    ]}
                                    name={`${item.name + 'arrayWithValue'}`}
                                />
                            </ProFormGroup>

                        </ProFormList>
                    )
                } else if ('multipleWithMap' === item.type) {
                    res = (
                        <ProFormList
                            {...commonProps}
                            rules={[
                                {
                                    required: item.required,
                                    validator: async (_, value) => {
                                        if (value && value.length > 0) {
                                            return;
                                        }
                                        throw new Error('至少要有一项！');
                                    },
                                },
                            ]}
                            itemRender={({ listDom, action }, { record, index }) => {
                                return (
                                    <ProCard
                                        bordered
                                        extra={action}
                                        title={`${index}.${item.label}_配置`}
                                        style={{
                                            marginBlockEnd: 8,
                                        }}
                                    >
                                        {listDom}
                                    </ProCard>
                                );
                            }}
                            creatorButtonProps={{
                                creatorButtonText: '新增配置组',
                            }}
                            deleteIconProps={{ tooltipText: '删除该配置组' }}

                        >
                            <ProFormList
                                name="lab4545els"
                            >

                                <ProFormGroup key="group">
                                    <ProFormText
                                        colProps={{
                                            span: 12
                                        }}
                                        // readonly={true}
                                        disabled={true}
                                        name="key"
                                    />
                                    <ProFormText
                                        colProps={{
                                            span: 12
                                        }}
                                        rules={[
                                            {
                                                validateTrigger: ['change', 'blur'],
                                                required: item.required,
                                                whitespace: true,
                                                message: `不能为空!`,
                                            }
                                        ]}
                                        name="value"
                                    />

                                </ProFormGroup>

                            </ProFormList>
                        </ProFormList>
                    )
                }
            }

            return res


        })
    }

    return (
        <div className={className}>
            {
                invokeRender()
            }
        </div>
    )
}


export default Index