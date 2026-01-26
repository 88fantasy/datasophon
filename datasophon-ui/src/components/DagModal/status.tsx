import { blue, gold, green, red } from "@ant-design/colors"
import { CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined, LoadingOutlined } from "@ant-design/icons"

export const T_PENDING = 'PENDING'
export const T_RUNNING = 'RUNNING'
export const T_SUCCESS = 'SUCCESS'
export const T_FAILED = 'FAILED'
export const T_CANCEL = 'CANCEL'




export const invokeGenStatusDom = ({
    val,
}) => {
    const statusIcon = {
        [T_SUCCESS]: {
            com: CheckCircleOutlined,
            style: {
                color: green.primary
            },
        },
        [T_FAILED]: {
            com: ExclamationCircleOutlined,
            style: {
                color: red.primary
            },
            status: 'exception'
        },
        [T_CANCEL]: {
            com: CloseCircleOutlined,
            style: {
                color: gold.primary
            }
        },
        [T_PENDING]: {
            com: ClockCircleOutlined,
        }
    }

    val = statusIcon[val] || {
        com: LoadingOutlined,
        style: {
            color: blue.primary
        },
        status: "active"
    }
    const Com = val.com



    return {
        com: <Com
            className="text-[16px] cursor-pointer"
            {...val}
        />,
        status: val.status
    }
}