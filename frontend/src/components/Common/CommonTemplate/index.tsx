import { grey } from "@ant-design/colors";
import { ProCard, ProForm, ProFormGroup, ProFormList, ProFormSelect, ProFormSlider, ProFormSwitch, ProFormText } from "@ant-design/pro-components";
import { Col } from "antd";
import { cloneDeep } from "lodash-es";
import type React from "react";
import { invokeMapShowMultiply } from "./utils";




const Index = ({
    templateData,
    className = '',
    namePrefix
}) => {



    const invokeRender = () => {

        console.log('templateData', templateData)

        const r = templateData.map((item, index) => {
            let res


            let label: React.ReactNode = item.label

            // item.type = 'multipleWithMap'


            if (
                // item.name !== item.label &&
                item.name
            ) {
                label = (
                    <div className="text-left">
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
                tooltip: item.description,
                label,
                placeholder: item.placeholder,
                name: namePrefix ? [...namePrefix, item.name].filter(Boolean) : item.name,
                key: item.name,
                initialValue: item.value || item.defaultValue
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
                            fieldProps={{
                                min: item.minValue,
                                max: item.maxValue,
                                marks: {
                                    0: item.minValue,
                                    100: item.maxValue
                                },
                            }}
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
                if (invokeMapShowMultiply(item)) {
                    res = (
                        <ProFormList
                            {...commonProps}
                            className="w-full"
                            rules={[
                                {
                                    required: item.required,
                                    validator: async (_, value) => {
                                        if (item.required) {
                                            value = value.map(val => val.value).join('')
                                            if (value && value.length > 0) {
                                                return;
                                            }
                                            throw new Error('至少要有一项！');
                                        } else {
                                            return
                                        }

                                    },
                                },
                            ]}
                        >
                            <ProFormGroup key="group">
                                <ProFormText
                                    width="xl"
                                    rules={[
                                        {
                                            required: item.required,
                                            whitespace: true,
                                            message: `${item.label}不能为空!`,
                                        }
                                    ]}
                                    name="value"
                                />
                            </ProFormGroup>

                        </ProFormList>
                    )
                } else if ('multipleWithKey' === item.type) {
                    res = (
                        <ProFormList
                            {...commonProps}
                            rules={[
                                {
                                    required: item.required,
                                    validator: async (_, value) => {
                                        if (!item.required || value && value.length > 0) {
                                            return;
                                        }
                                        throw new Error('至少要有一项！');


                                    },
                                },
                            ]}
                        >
                            <ProFormGroup key="group">
                                <ProFormText
                                    width="md"
                                    rules={[
                                        {
                                            required: item.required,
                                            message: `${item.label}key不能为空!`,
                                        }
                                    ]}
                                    name="key"
                                />
                                <ProFormText
                                    width="md"
                                    rules={[
                                        {
                                            required: item.required,
                                            message: `${item.label}value不能为空!`,

                                        }
                                    ]}
                                    name="value"
                                />
                            </ProFormGroup>

                        </ProFormList>
                    )
                } else if ('multipleWithMap' === item.type) {
                    const creatorRecord = cloneDeep(item.defaultValue[0])
                    creatorRecord.items.map(v => {
                        v.value = ''
                    })

                    res = (
                        <ProFormList
                            {...commonProps}
                            rules={[
                                {
                                    required: item.required,
                                    validator: async (_, value) => {
                                        if (!item.required || value && value.length > 0) {
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
                            creatorRecord={creatorRecord}
                            deleteIconProps={{ tooltipText: '删除该配置组' }}

                        >

                            <ProFormList
                                name="items"
                                creatorButtonProps={false}
                                copyIconProps={false}
                                deleteIconProps={false}
                            >

                                <ProFormGroup key="group">
                                    <ProFormText
                                        width="md"
                                        // readonly={true}
                                        disabled={true}
                                        name="key"
                                    />
                                    <ProFormText
                                        width="md"
                                        rules={[
                                            {
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
                } else {
                    console.warn('未兼容表单类型', item)
                }
            }


            return res


        })

        return r
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