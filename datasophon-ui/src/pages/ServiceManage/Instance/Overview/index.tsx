import { memo, useContext } from "react"
import { ProxyContext } from "../../../../context/proxyContext"
import { useParams } from "react-router"
import { useInstanceHooks } from "../../../../hooks/useInstanceHooks"

const Index = () => {
    // const { serviceListMapRef } = useContext(ProxyContext)
    // const { instanceId } = useParams()

    // const obj = serviceListMapRef.current[instanceId]
    const {
        // serviceListMapRef,
        // instanceId,
        obj
    } = useInstanceHooks(ProxyContext)

    return (
        <iframe className="w-full h-[72vh]" src={obj.dashboardUrl}>
        </iframe>
    )
}


export default memo(Index)