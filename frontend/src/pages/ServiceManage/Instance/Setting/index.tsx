import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { API } from "../../../../api"
import { useParams } from "react-router"
import { axiosPost } from "../../../../api/request"
import { Menu, Select, Space, Spin, Tabs } from "antd";
import { ProCard, ProForm } from "@ant-design/pro-components";
import CommonTemplate from "../../../../components/Common/CommonTemplate";
import { invokeFormatTemplateData, invokeHandleTemplateData } from "../../../../components/Common/CommonTemplate/utils";
import { useInstanceHooks } from "../../../../hooks/useInstanceHooks";
import { ProxyContext } from "../../../../context/proxyContext";
import { showMsgAfferRequest } from "../../../../utils/util";


const Index = () => {

    const { instanceId, clusterId } = useParams()
    const {
        // serviceListMapRef,
        // instanceId,
        obj
    } = useInstanceHooks(ProxyContext)
    const originTemplateDataRef = useRef()
    const [roleType, setRoleType] = useState({})
    const [roleGroupList, setRoleGroupList] = useState([])
    const [currentId, setCurrentId] = useState()
    const [verSionList, setVerSionList] = useState([])
    const [currentVersion, setCurrentVersion] = useState()
    const [templateData, setTemplateData] = useState()
    const [hadInit, setHadInit] = useState()


    const versionOpts = useMemo(() => {
        return verSionList.map(val => {
            return {
                value: val,
                label: val
            }
        })
    }, [verSionList])


    const getServiceConfigOption = useCallback(async (obj) => {

        const params = {
            serviceInstanceId: instanceId,
            page: 1,
            pageSize: 10000,
            ...obj
        };
        const res = await axiosPost(API.getConfigInfo, params)



        if (res.code === 200) {
            setTemplateData(
                invokeHandleTemplateData(res.data)
            )
            originTemplateDataRef.current = res.data
        }

        return res
    }, [instanceId])

    const onVersionChange = useCallback((e) => {
        setCurrentVersion(e)
        getServiceConfigOption({
            version: e,
            roleGroupId: currentId
        })
    }, [currentId, getServiceConfigOption])

    const memoRoleGroupList = useMemo(() => {
        const res = roleGroupList.map(val => {
            return {
                key: val.id,
                label: val.roleGroupName
            }
        })

        // res.unshift({
        //     key: '',
        //     label: (

        //     ),
        //     disabled: true
        // })

        return res
    }, [roleGroupList])






    const getConfigVersion = useCallback(async (roleGroupId = '') => {
        const params = {
            serviceInstanceId: instanceId,
            roleGroupId,
        };

        const res = await axiosPost(API.getConfigVersion, params)
        if (res.code === 200) {
            const verSionList = res.data;


            setVerSionList(verSionList)

            if (verSionList.length > 0) {
                const version = verSionList[0];
                setCurrentVersion(version)
                return getServiceConfigOption({
                    version,
                    roleGroupId
                });
            }
        }
    }, [getServiceConfigOption, instanceId])

    const getServiceRoleType = useCallback(async () => {
        const params = {
            serviceInstanceId: instanceId,
        }
        const res = await axiosPost(API.getRoleGroupList, params)


        if (res.code === 200) {
            const data = res.data || []
            data.map(val => {
                val.id = String(val.id)
            })
            setRoleGroupList(data)
            let id
            if (data.length) {
                id = data[0].id
                setCurrentId(id)
            }


            return getConfigVersion(id)
        }

    }, [getConfigVersion, instanceId])


    const onMenuClick = useCallback(({ key }) => {
        setCurrentId(key)
        getConfigVersion(key)
    }, [getConfigVersion])


    const onFinish = useCallback(async (values) => {
        values = invokeFormatTemplateData(originTemplateDataRef.current, values)

        // console.log('values', values)

        const saveParam = {
            clusterId,
            serviceName: obj.serviceName,
            serviceConfig: JSON.stringify(values),
            roleGroupId: currentId,
        };


        const res = await axiosPost(
            API.saveServiceConfig,
            saveParam
        );

        showMsgAfferRequest(res)

        if (res.code === 200) {
            getConfigVersion(currentId)
        }

        console.log('saveParam', saveParam)
    }, [clusterId, currentId, getConfigVersion, obj.serviceName])

    const invokeInit = useCallback(async () => {
        await getServiceRoleType()

        setHadInit(true)
    }, [getServiceRoleType])

    useEffect(() => {
        invokeInit()
    }, [invokeInit])


    return (
        <div className="flex flex-1 min-h-[72vh] h-full w-full">
            {
                !hadInit ? <Spin className="w-full flex" /> : <>
                    <Menu
                        className="w-[200px] h-full !border-none"
                        items={memoRoleGroupList}
                        selectedKeys={[currentId]}
                        onClick={onMenuClick}
                    />
                    <div className="px-[20px] border-l border-[#f0f0f0] flex-1">
                        <Space wrap className="!text-black mb-[20px]">
                            版本:
                            <Select
                                className="w-[100px]"
                                onChange={onVersionChange}
                                options={versionOpts}
                                value={currentVersion}
                            />
                        </Space>
                        {
                            templateData &&
                            <ProForm
                                layout="horizontal"
                                colon={false}
                                labelCol={{ span: 8 }}
                                labelAlign="left"
                                onFinish={onFinish}
                                submitter={{
                                    resetButtonProps: false
                                }}

                            >
                                <CommonTemplate
                                    templateData={templateData}
                                    className="152"
                                />
                            </ProForm>
                        }

                    </div>
                </>

            }





        </div>
    )
}


export default memo(Index)