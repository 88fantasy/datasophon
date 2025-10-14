import { Modal } from "antd"



let instance: typeof Modal

export const invokeSetModal = (val: typeof Modal) => {
  instance = val
}


export const invokeGetModal = () => {
  return {
    Modal: instance || Modal
  }
}
