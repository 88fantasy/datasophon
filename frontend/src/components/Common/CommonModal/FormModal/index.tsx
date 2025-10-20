
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ProForm, ProFormCascader, ProFormSelect, ProFormText, ProFormTextArea, ProFormTreeSelect, ProTable } from '@ant-design/pro-components';
import { isEmpty, requireRules, showMsgAfferRequest } from '../../../../utils/util';
import { cloneDeep } from 'lodash-es';
import { API } from '../../../../api';
import { axiosJsonPost } from '../../../../api/request';
import { invokeRenderForm } from './utils';





const Index = (props) => {

    const {
        record = {},
        invokeInjectConfirmEvent,
        apiConfig = {},
        formConfig = [],
        proFormProps = {},
        paramsFn,
        initCallback
    } = props

    let {
        formRef
    } = props

    const innerFormRef = useRef()

    if (!formRef) {
        formRef = innerFormRef
    }


    const onComfirm = useCallback(async () => {
        try {
            const validateFieldsRes = await formRef.current.validateFields()
            if (validateFieldsRes) {
                const params = {
                    ...validateFieldsRes,

                }

                if (!isEmpty(record.id)) {
                    params.id = record.id
                }

                if (paramsFn) {
                    params = paramsFn({
                        params: cloneDeep(params),
                        ...props
                    })
                }

                let api = apiConfig.add
                if (params.id && apiConfig.update) {
                    api = apiConfig.update
                }

                let res

                if (typeof api === 'function') {
                    res = await api(params)
                } else {
                    res = await axiosJsonPost(api, params)
                }

                showMsgAfferRequest(res)


                if (res.code === 200) {
                    return res
                } else {
                    return false
                }
            }

        } catch (error) {
            console.error(error)
            return false
        }
    }, [apiConfig.add, apiConfig.update, record.id])


    const memoInitialValue = useMemo(() => {
        const res = cloneDeep(record)
        delete res.password
        return res
    }, [record])




    useEffect(() => {
        invokeInjectConfirmEvent(onComfirm)
    }, [invokeInjectConfirmEvent, onComfirm])

    useEffect(() => {
        // Object.assign(ruleForm, record || {});


        // setRuleForm(ruleForm)
        typeof initCallback === 'function' && initCallback({ formRef })
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const invokeRenderInfo = () => {
        return (
            <>
                <ProForm
                    formRef={formRef}
                    submitter={false}
                    initialValues={memoInitialValue}
                    {...proFormProps}
                >

                    {
                        invokeRenderForm(formConfig)
                    }
                </ProForm>
            </>
        )
    }


    return invokeRenderInfo()

};

export default Index

