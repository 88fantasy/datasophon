"use client";
import { useParams } from "react-router"
import { ClusterGlobalContext, useClusterGlobalContext } from "../../../context/clusterGlobalContext"
import { lazy, memo, Suspense, useContext, useMemo } from "react"
import { cloneDeep } from "lodash-es"
import { ProxyContext } from "../../../context/proxyContext";
import CommonTabs from "../../../components/Common/CommonTabs";
import { useInstanceHooks } from "../../../hooks/useInstanceHooks";
// import Overview from "./Overview";

const Overview = lazy(() => import('./Overview'));
const Instance = lazy(() => import('./Instance'));
const Setting = lazy(() => import('./Setting'));
const SourceSetting = lazy(() => import('./SourceSetting'));


const invokeRenderAsyncCompnent = (Component) => {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <Component />
        </Suspense>
    )
}

const Index = () => {

    const params = useParams()
    // const { invokeGetServiceListMap, serviceListMapRef } = useClusterGlobalContext()
    // const { dashboardUrl, invokeGetServiceListMap, serviceListMapRef } = useClusterGlobalContext()

    // console.log('子页面')
    // const obj = invokeGetServiceListMap()

    // console.log('invokeGetServiceListMap', cloneDeep(serviceListMapRef.current))
    const { proxyContext, serviceListMapRef, a } = useContext(ProxyContext)


    const {
        // serviceListMapRef,
        // instanceId,
        obj
    } = useInstanceHooks(ProxyContext)

    const memoTabItem = useMemo(() => {
        return [
            obj.dashboardUrl && {
                label: '概览',
                key: 'Overview',
                asyncChildren: Overview
            },
            {
                label: '实例',
                key: 'Instance',
                asyncChildren: Instance

            },
            {
                label: '配置',
                key: 'Setting',
                asyncChildren: Setting

            },
            {
                label: '资源配置',
                key: 'SourceSetting',
                asyncChildren: SourceSetting
            },
        ]
    }, [obj])

    return (
        <CommonTabs
            memoTabItem={memoTabItem}
        />
    )
}


export default Index