"use client";
import { useParams } from "react-router"
import { ClusterGlobalContext, useClusterGlobalContext } from "../../../context/clusterGlobalContext"
import { lazy, memo, Suspense, useCallback, useContext, useEffect, useMemo, useState } from "react"
import { cloneDeep } from "lodash-es"
import { ProxyContext } from "../../../context/proxyContext";
import CommonTabs from "../../../components/Common/CommonTabs";
import { useInstanceHooks } from "../../../hooks/useInstanceHooks";
import { API } from "../../../api";
import { axiosPost } from "../../../api/request";
import { Button, Dropdown, Space } from "antd";
import { DownOutlined } from "@ant-design/icons";
// import Overview from "./Overview";

const Overview = lazy(() => import('./Overview'));
const Instance = lazy(() => import('./Instance'));
const Setting = lazy(() => import('./Setting'));
const SourceSetting = lazy(() => import('./SourceSetting'));
const Queue = lazy(() => import('./Queue'));




const Index = () => {

    const {
        // serviceListMapRef,
        instanceId,
        obj
    } = useInstanceHooks(ProxyContext)

    const [webUis, setWebUis] = useState([])

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
        ].filter(Boolean)
    }, [obj.dashboardUrl, obj.serviceName])


    const tabBarExtraContent = useMemo(() => {


        const items = webUis?.map(val => {
            return {
                key: val.name,
                label: val.name,

                onClick: () => {
                    window.open(val.webUrl)
                }
            }
        })

        return {
            right: !!items?.length && (
                <Dropdown menu={{ items }}>
                    <Button
                        variant="filled"
                        color="default"
                    >
                        WebUI
                    </Button>
                </Dropdown>

            )
        }
    }, [webUis?.length])


    const getWebUis = useCallback(async () => {
        const res = await axiosPost(API.getWebUis, {
            serviceInstanceId: instanceId,
        })
        if (res.code === 200) {
            setWebUis(res.data || []);
        }
    }, [instanceId])


    useEffect(() => {
        getWebUis()
    }, [getWebUis])


    return (
        // <div className="h-[78vh] flex flex-col">
        <CommonTabs
            // className="flex-1"
            memoTabItem={memoTabItem}
            tabBarExtraContent={tabBarExtraContent}
        />
        // </div>
    )
}


export default memo(Index)