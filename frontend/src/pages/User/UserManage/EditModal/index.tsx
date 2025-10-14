import { ProForm, ProFormRadio, ProFormText, type ProFormInstance } from '@ant-design/pro-components';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import _ from 'lodash'
import { isEmpty, requireRules, showMsgAfferRequest } from '~/utils/utils';
import { changePasswordApi, updatePasswordApi } from '~/services/login';
import { getPasswordValidator } from '~/utils/validators';



const Index = ({
  record,
  invokeInjectConfirmEvent,
  secret,
  username,
  encrypt
}) => {

  const formRef = useRef()

  const [ruleForm, setRuleForm] = useState({
    name: ""
  })

  const invokeGetValueList = useCallback(() => {
    // console.log(formRef.current?.getFieldsValue())
    // const { valueList = [] } = formRef.current?.getFieldsValue() || {};

    return formRef.current?.getFieldsValue() || {}

  }, [formRef])



  const invokeCheckIsSame = useCallback((key, value) => {
    const valueList = invokeGetValueList()
    // console.log(valueList)
    let res

    if (value) {
      value = value.trim()
    }

    // for (const item of valueList) {
    if (valueList[key] === value) {
      res = true
    }

    // if (res) {
    //   break
    // }
    // }

    return res

  }, [invokeGetValueList])


  const newPasswordValid = useMemo(() => {
    const fn = (rule, value) => {
      if (!isEmpty(value) && invokeCheckIsSame('oldPassword', value)) {
        return Promise.reject(new Error(`新密码不能与旧密码相同`))
      } else {

        return getPasswordValidator()(rule, value)
      }
    }

    return [
      ...requireRules,
      () => (
        {
          validator: fn
        }
      )
    ]
  }, [])

  const oldPasswordValid = useMemo(() => {
    const fn = (rule, value) => {
      if (!invokeCheckIsSame('newPassword', value)) {
        return Promise.reject(new Error(`两次输入的密码不一致`))
      } else {
        return Promise.resolve()
      }
    }

    return [
      ...requireRules,

      () => (
        {
          validator: fn
        }
      )
    ]
  }, [])

  const onOldPasswordChange = useCallback((e) => {
    // const value = e.target.value
    const invokeGetValueListRes = invokeGetValueList()


    if (invokeGetValueListRes.newPassword) {
      formRef.current.validateFields(['newPassword'])
    }

    if (invokeGetValueListRes.confirmPassword) {
      formRef.current.validateFields(['confirmPassword'])
    }


  }, [])

  const onNewPasswordChange = useCallback((e) => {
    // const value = e.target.value
    const invokeGetValueListRes = invokeGetValueList()



    if (invokeGetValueListRes.confirmPassword) {
      formRef.current.validateFields(['confirmPassword'])
    }


  }, [])

  const onComfirm = useCallback(async () => {
    // console.log("onComfirm", proFormRef.current, formRef);
    try {
      const validateFieldsRes = await formRef.current.validateFields()

      let res
      const params = {
        oldPassword: validateFieldsRes.oldPassword,
        newPassword: validateFieldsRes.newPassword
      }
      if (secret && username) {
        res = await changePasswordApi({
          ...params,
          secret,
          username
        })
      } else {
        res = await updatePasswordApi(params)
      }

      if (res.success) {
        showMsgAfferRequest(res)

        return res
      } else {
        return false
      }



    } catch (error) {
      console.error(error)
      return false
    }
  }, [encrypt, secret, username])

  useEffect(() => {

    invokeInjectConfirmEvent(onComfirm)

  }, [invokeInjectConfirmEvent, onComfirm])


  return (
    <ProForm
      formRef={formRef}
      grid={true}
      submitter={false}
    >
      <ProFormText.Password
        name="oldPassword"
        label="旧密码"
        rules={requireRules}
        fieldProps={{
          onChange: onOldPasswordChange
        }}
      />
      <ProFormText.Password
        name="newPassword"
        label="新密码"
        rules={newPasswordValid}
        fieldProps={{
          onChange: onNewPasswordChange
        }}

      />
      <ProFormText.Password
        name="confirmPassword"
        label="确定新密码"
        rules={oldPasswordValid}
      />

    </ProForm>
  );
};

export default Index
