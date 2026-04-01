"use client";
import { useParams } from "react-router"
import { lazy, memo, Suspense, useCallback, useContext, useEffect, useMemo, useState } from "react"
import { cloneDeep } from "lodash-es"
import { ProxyContext } from "../../../context/proxyContext";
import CommonTabs from "../../../components/Common/CommonTabs";
import { useInstanceHooks } from "../../../hooks/useInstanceHooks";
import { API } from "../../../api";
import { axiosJsonPost, axiosPost } from "../../../api/request";
import { Button, Dropdown, Space } from "antd";
import { useClusterFromParams } from "../../../hooks/useClusterFromParams";
import { T_K8S, T_PHYSICAL } from "../../../constants/clusterType";
import { isEmpty } from "../../../utils/util";
import resourceType from "../../../constants/resourceType";
// import Overview from "./Overview";

const Overview = lazy(() => import('./Overview'));
const Instance = lazy(() => import('./Instance'));
const Setting = lazy(() => import('./Setting'));
const SourceSetting = lazy(() => import('./SourceSetting'));
const Queue = lazy(() => import('./Queue'));
const K8s = lazy(() => import('./K8s'));




const Index = () => {

    const {
        // serviceListMapRef,
        instanceId,
        obj
    } = useInstanceHooks(ProxyContext)

    const {
        memoCluster
    } = useClusterFromParams()

    const [webUis, setWebUis] = useState([])
    const [hadInit, setHadInit] = useState(false)
    const [k8sInstanceListResourceType, setK8sInstanceListResourceType] = useState([])

    const memoTabItem = useMemo(() => {



        let k8sArr = []


        if (memoCluster.archType === T_K8S) {
            k8sArr.push(
                ...resourceType.map(item => ({
                    label: item.label,
                    key: item.value,
                    props: {
                        resourceType: item.value
                    },
                    asyncChildren: K8s
                }))
            )
        }

        k8sArr = k8sArr.filter(val => k8sInstanceListResourceType.includes(val.key))

        return [
            obj.dashboardUrl && {
                label: '概览',
                key: 'Overview',
                asyncChildren: Overview
            },
            (memoCluster.archType === T_PHYSICAL || isEmpty(memoCluster.archType)) && {
                label: '实例',
                key: 'Instance',
                asyncChildren: Instance

            },
            ...k8sArr,
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
    }, [k8sInstanceListResourceType, memoCluster.archType, obj.dashboardUrl, obj.serviceName])


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
    }, [webUis])


    const getWebUis = useCallback(async () => {

        if (memoCluster.archType === T_K8S) {
            const res = await axiosJsonPost(API.k8sInstanceListResourceType, {
                instanceId,
            })
            if (res.code === 200) {
                setK8sInstanceListResourceType(res.data || []);
            }
        } else {
            const res = await axiosPost(API.getWebUis, {
                serviceInstanceId: instanceId,
            })
            if (res.code === 200) {
                setWebUis(res.data || []);
            }
        }

        setHadInit(true)

    }, [instanceId, memoCluster.archType])


    useEffect(() => {
        getWebUis()
    }, [getWebUis])


    return hadInit && (
        // <div className="h-[78vh] flex flex-col">
        <CommonTabs
            // className="flex-1"
            memoTabItem={memoTabItem}
            tabBarExtraContent={tabBarExtraContent}
            bindUrl={true}
        />
        // </div>
    )
}


export default memo(Index)