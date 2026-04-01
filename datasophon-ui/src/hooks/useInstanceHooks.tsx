import { useContext } from "react"
import { useParams } from "react-router-dom"

export const useInstanceHooks = (ctx) => {
    const { serviceListMapRef } = useContext(ctx)

    const { instanceId } = useParams()

    const obj = serviceListMapRef.current[instanceId] || {}

    return {
        instanceId,
        obj,
        serviceListMapRef
    }
}