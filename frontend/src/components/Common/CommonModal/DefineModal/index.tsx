// import { Modal } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { invokeGetModal } from "../modalInstance";
// import { GlobalStyles } from 'styles/globalStyles';
import _ from 'lodash'


const Index = (initProps) => {


  const {
    Modal
  } = invokeGetModal()


  const {
    config,
    visiable,
    getContainer
  } = initProps

  console.log('config', config)

  const fnRef = useRef({})

  const [state, setState] = useState({
    visiable,
    pending: false,
  })


  const onClosed = useCallback(() => {
    initProps?.onClosed()
  }, [initProps])


  const onCancelClickProxy = useCallback((e) => {


    setState({
      ...state,
      visiable: false
    })


    if (e) {
      // 纯取消
      const fn = config.onCancelClick || config.onCancel
      fn && fn(e)
    }

  }, [config.onCancel, config.onCancelClick, state])

  const onOkClickProxy = useCallback(async () => {

    setState({
      ...state,
      pending: true
    })


    let fnRefRes
    if (fnRef.current.onOk) {
      fnRefRes = await fnRef.current.onOk()
    }


    let visiable = state.visiable

    if (fnRefRes !== false) {
      const onOkFn = config.onOkClick || config.onOk
      const res = onOkFn && await onOkFn(fnRefRes)

      if (res !== false) {
        visiable = false
        onCancelClickProxy()
      }
    }


    setState({
      ...state,
      pending: false,
      visiable,
    })
  }, [config, onCancelClickProxy, state])



  useEffect(() => {
    if (!config.dialogConfig) {
      config.dialogConfig = {}
    }


    if (config.title && !config.dialogConfig.title) {
      config.dialogConfig.title = config.title
    }

  }, [config, initProps.onOkClick, onOkClickProxy])

  const invokeInjectConfirmEvent = useCallback((fn) => {
    fnRef.current.onOk = fn
  }, [])

  return (
    <Modal
      data-component-CommonModal="true"
      open={state.visiable}
      width={'50vw'}
      destroyOnClose={true}
      maskClosable={false}
      afterClose={onClosed}
      onCancel={onCancelClickProxy}

      okButtonProps={{
        loading: state.pending,
        onClick: onOkClickProxy,
      }}
      // cancelButtonProps={{
      //   onClick: onCancelClickProxy.bind(_.noop, 'btn')
      // }}
      getContainer={getContainer}
      {
      ...config.dialogConfig
      }
    >
      {
        typeof config.render === 'function' && config.render({
          onCancelClickProxy,
          onOkClickProxy,
          getContainer,
          ...config,
          invokeInjectConfirmEvent
        })
      }
      {/* <GlobalStyles /> */}
    </Modal>
  )
};


export default Index
