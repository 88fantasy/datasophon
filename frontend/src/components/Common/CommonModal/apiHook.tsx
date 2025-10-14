import elConfigProviderHocFn from "./elConfigProviderHocFn";
import React from "react";
import { message } from "antd";
import injectLocationChange from "../../../utils/injectLocationChange";
import { invokeGenerateElId } from "../../../utils/util";
import { hydrateRoot } from "react-dom/client";

injectLocationChange()


let arrByRouteMap: { [key: string]: any } = {};
export const invokeCloseAllModal = () => {

  // ElMessage.closeAll()

  Object
    .values(arrByRouteMap)
    .forEach(val => {
      val.forEach(v => {
        v.closeModal()
      })
    })

  arrByRouteMap = {}
}

window.addEventListener('locationchange', invokeCloseAllModal)

const index = (com, fn) => {

  const currentPath = window.location.href


  const elId = invokeGenerateElId();

  let componentInstance: React.ReactElement | null = null;

  let wrapperEl: HTMLElement | null = null;

  const getContainer = () => {
    return document.getElementById(elId);
  }

  const closeModal = () => {
    componentInstance = null;
    if (document.getElementById(elId) && wrapperEl) {
      document.body.removeChild(wrapperEl);
    }


    const aimArr = arrByRouteMap[currentPath] || []


    const index = aimArr.findIndex(val =>
      val.elId === elId
    )

    arrByRouteMap[currentPath].splice(index, 1)
  };


  return async (options = {}) => {
    console.log("apiHook", com, options);

    if (com.hackerFnList) {
      com.hackerFnList.forEach(cb => {
        try {
          cb(options, () => {
            return componentInstance
          })
        } catch (err) {
          console.error(err)
        }
      })
    }

    if (!fn && options.beforeOpen) {
      fn = options.beforeOpen
    }

    const propsData = {
      visiable: true,
      elId,
      getContainer,
      closeModal,
      ...options,
    };


    if (typeof fn === 'function') {

      const messageKey = invokeGenerateElId()

      message.info({
        duration: 0,
        content: '数据加载中...',
        key: messageKey
      })



      try {
        await fn(options, propsData);
        message.destroy(messageKey)

      } catch (error) {
        message.destroy(messageKey)

        console.error(error)

        return
      }

    }


    Object.assign(propsData, options)


    if (currentPath !== window.location.href) {
      return
    }

    const props = {
      onClosed: () => {
        closeModal();
      },
      ...propsData,
    }

    console.log('props', props)

    if (!componentInstance) {
      componentInstance = React.createElement(elConfigProviderHocFn(com), props);
      console.log('componentInstance', componentInstance)
      // componentInstance = (() => {
      //   const Dom = elConfigProviderHocFn(com)
      //   return <Dom {...props} />
      // })()
    } else {

      componentInstance = React.cloneElement(componentInstance, props);
    }


    if (!document.getElementById(elId)) {
      wrapperEl = document.createElement("div");
      // wrapperEl.setAttribute('class', `basic-default-modal dark-modal formilyModal customtheme`)
      wrapperEl.id = elId;
      document.body.appendChild(wrapperEl);
    }




    // componentInstance.props.visiable = true;

    if (wrapperEl) {
      const hydrateRes = hydrateRoot(wrapperEl, componentInstance)
      console.log('hydrateRes', hydrateRes)
    }


    const res = {
      elId,
      closeModal,
      componentInstance,
    }

    if (!arrByRouteMap[currentPath]) {
      arrByRouteMap[currentPath] = []
    }

    arrByRouteMap[currentPath].push(res)

    return res
  };
};


export default index;
