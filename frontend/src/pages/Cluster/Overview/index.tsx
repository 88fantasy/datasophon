import { useEffect } from "react"
import { useClusterGlobalContext } from "../../../context/clusterGlobalContext"

const Index = () => {


    const { dashboardUrl, setProCardBodyStyle } = useClusterGlobalContext()


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