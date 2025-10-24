import { useContext, useEffect } from "react"
import { useClusterGlobalContext } from "../../../context/clusterGlobalContext"
import { ProxyContext } from "../../../context/proxyContext"

const Index = () => {


    const { dashboardUrl, setProCardBodyStyle } = useContext(ProxyContext)

    console.log('dashboardUrl', dashboardUrl)

    useEffect(() => {
        setProCardBodyStyle?.({
            padding: '1px'
        })
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return (
        <iframe className="w-full h-[82.5vh]" src={dashboardUrl}>
        </iframe>

    )
}


export default Index