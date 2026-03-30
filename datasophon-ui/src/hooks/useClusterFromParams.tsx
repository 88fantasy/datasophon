import { useMemo } from "react"
import { T_PHYSICAL } from "../constants/clusterType"
import { useParams } from "react-router-dom"

export const useClusterFromParams = () => {
    const { clusterId: cluster, instanceId } = useParams()


    const memoCluster = useMemo(() => {
        if (!cluster) return null


        const arr = cluster.split('_')


        return {
            clusterId: arr[0],
            archType: arr[1] || T_PHYSICAL
        }
    }, [cluster])



    const clusterId = useMemo(() => {
        return memoCluster?.clusterId
    }, [memoCluster?.clusterId])



    return {
        clusterId,
        memoCluster
    }

}