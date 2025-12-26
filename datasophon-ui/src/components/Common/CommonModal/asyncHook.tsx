import { message } from "antd"
import { invokeGenerateElId } from "../../../utils/util"

const index = (fn) => {

    let pending

    let initial
    const promiseFn = async (resolve) => {
        const key = invokeGenerateElId()

        message.loading({
            content: '加载中...',
            duration: 0,
            key
        })

        const setTimeoutFn = async () => {
            const modelApi = await fn()
            message.destroy(key)
            // pending = false
            clearTimeout(pending)
            pending = undefined
            initial = true
            resolve(modelApi)
        }


        if (!initial) {
            pending = setTimeout(setTimeoutFn, 1 * 1000)
        } else {
            setTimeoutFn()
        }
    }

    const fnKey = invokeGenerateElId()
    const res = () => {

        if (pending) {
            return
        }

        return new Promise(promiseFn)
    }

    res.key = fnKey


    return res

}


export default index