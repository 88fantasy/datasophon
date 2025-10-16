import { grey } from "@ant-design/colors";
import { ProFormList, ProFormSelect, ProFormSlider, ProFormSwitch, ProFormText } from "@ant-design/pro-components";
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




            if (
                // item.name !== item.label &&
                item.name
            ) {
                label = (
                    <>
                        {label}
                        <span
                            style={{
                                color: grey[1]
                            }}
                            className="text-[12px] ml-[5px]"
                        >
                            {item.name}
                        </span>
                    </>
                )
            }


            const commonProps = {
                extra: item.description,
                label,
                placeholder: item.placeholder,
                name: item.name
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
                if ('multipleWithMap' === item.type) {
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