import { ProForm, ProFormText } from "@ant-design/pro-components"
import { reactComponentChecker } from "../../../../utils/reactComponentChecker"


export const invokeTransColToFormConfig = (config) => {
    if (config.columns && !config.formConfig) {
        config.formConfig = config.columns
            .filter(val => {
                return val.dataIndex
            })
            .map(val => {
                return {
                    // ...val,
                    name: val.dataIndex || val.name,
                    label: val.title || val.label,
                    ...(val?.formItemProps || {}),
                    // ...val,
                    com: val.com
                }
            })
    }


    // if (config.formConfig) {
    //     config.formConfig.map(val => {
    //         val.com = val.com || ProFormText
    //     })
    // }

    return config
}

export const invokeRenderForm = (formConfig) => {
    return formConfig.map(val => {
        const Com = val.com || ProFormText
        return <Com
            key={val.name}
            {...val}

        />
    })
}



export const invokeRenderFormDom = (props) => {

    invokeTransColToFormConfig(props)

    const {
        proFormProps,
        formRef,
        memoInitialValue,
        formConfig,
        formItemRender
    } = props


    let Com


    if (reactComponentChecker.isElement(formItemRender)) {
        Com = formItemRender
    } else if (reactComponentChecker.isComponent(formItemRender)) {
        Com = formItemRender(props)
    } else {
        Com = invokeRenderForm(formConfig)
    }


    return <>
        <ProForm
            formRef={formRef}
            // formRef={formRef}
            // formRef
            submitter={false}
            initialValues={memoInitialValue}
            {...proFormProps}
        >

            {
                Com
            }
        </ProForm>
    </>
}