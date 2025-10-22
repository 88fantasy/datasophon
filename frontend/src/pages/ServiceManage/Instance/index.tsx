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
const Queue = lazy(() => import('./Queue'));




const Index = () => {

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
            obj.serviceName === 'YARN' && {
                label: '资源配置',
                key: 'SourceSetting',
                asyncChildren: Queue
            },
        ]
    }, [obj])

    return (
        // <div className="h-[78vh] flex flex-col">
        <CommonTabs
            // className="flex-1"
            memoTabItem={memoTabItem}
        />
        // </div>
    )
}


export default memo(Index)