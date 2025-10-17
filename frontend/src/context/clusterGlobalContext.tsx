import { createContext, memo, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useMatch, useParams } from "react-router-dom";
import { invokeGetRouteByPath } from "../utils/routerUtils";
import { axiosPost } from "../api/request";
import { API } from "../api";
import type { AnyObject } from "antd/es/_util/type";


let timer


export const ClusterGlobalContext = createContext({})


export const ClusterGlobalProvider = ({
    children,
    config
}) => {
    const [runningClusterList, setRunningClusterList] = useState()
    const [dashboardUrl, setDashboardUrl] = useState()
    const [hadInit, setHadInit] = useState(false)

    const matchRoute = useRef(invokeGetRouteByPath())
    
    const clusterId = useMemo(() => {
        return matchRoute.current.params.clusterId
    }, [])

    const invokeGetClusterId = () => {
        return matchRoute.current.params.clusterId
    }

    // const { clusterId } = matchRoute.params



    const invokeGetDashboardUrl = useCallback(async () => {

        const res = await axiosPost(API.getDashboardUrl, { clusterId })

        if (res.code === 200) {
            clearTimeout(timer)
            setDashboardUrl(res.data)
        }

        // .then(res => {
        //     // commit('setDashboardUrl', res.data)
        //     // dispatch('getServiceList')
        // })
    }, [clusterId])

    const invokeGetRunningClusterList = useCallback(async () => {
        const res = await axiosPost(API.runningClusterList, {})
        if (res.code === 200) {
            const arr = res.data.map(item => {
                return {
                    label: item.clusterName,
                    value: item.id,
                    originData: item
                }
            })

            setRunningClusterList(arr)
            await invokeGetDashboardUrl()
        }
    }, [invokeGetDashboardUrl])


    const invokeInit = useCallback(async () => {

        if (runningClusterList?.length) {
            return
        }
        if (/Cluster\/\:clusterId\//gi.test(matchRoute.current.route.path)) {
            await invokeGetRunningClusterList()

        }

        setHadInit(true)
    }, [invokeGetRunningClusterList, runningClusterList?.length])

    useEffect(() => {
        invokeInit()
    }, [invokeInit])


    return (
        <ClusterGlobalContext.Provider
            value={{
                ...config,
                runningClusterList,
                dashboardUrl,
                clusterId
            }}
        >
            {
                hadInit && children
            }
        </ClusterGlobalContext.Provider>
    )
}


export const useClusterGlobalContext = () => useContext(ClusterGlobalContext)