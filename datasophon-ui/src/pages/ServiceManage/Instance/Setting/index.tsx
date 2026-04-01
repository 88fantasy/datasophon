import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { API } from "../../../../api"
import { useParams } from "react-router"
import { axiosGet, axiosJsonPost, axiosPost } from "../../../../api/request"
import { Menu, Select, Space, Spin, Tabs } from "antd";
import { ProCard, ProForm } from "@ant-design/pro-components";
import CommonTemplate from "../../../../components/Common/CommonTemplate";
import { invokeFormatTemplateData, invokeHandleTemplateData } from "../../../../components/Common/CommonTemplate/utils";
import { useInstanceHooks } from "../../../../hooks/useInstanceHooks";
import { ProxyContext } from "../../../../context/proxyContext";
import { isEmpty, showMsgAfferRequest } from "../../../../utils/util";
import { useClusterFromParams } from "../../../../hooks/useClusterFromParams";
import { T_K8S, T_PHYSICAL } from "../../../../constants/clusterType";
import Helm from "./Helm";


const Index = () => {

    const { instanceId } = useParams()

    const { clusterId, memoCluster } = useClusterFromParams()
    const {
        // serviceListMapRef,
        // instanceId,
        obj
    } = useInstanceHooks(ProxyContext)
    const originTemplateDataRef = useRef()
    const [roleGroupList, setRoleGroupList] = useState([])
    const [currentId, setCurrentId] = useState()
    const [verSionList, setVerSionList] = useState([])
    const [currentVersion, setCurrentVersion] = useState()
    const [templateData, setTemplateData] = useState()
    const [hadInit, setHadInit] = useState()


    const versionOpts = useMemo(() => {
        return verSionList.map(val => {

            if (typeof val === 'string' || typeof val === 'number') {
                return {
                    value: val,
                    label: val
                }
            } else {
                return {
                    value: val.id,
                    label: val.version
                }
            }


        })
    }, [verSionList])


    const getServiceConfigOption = useCallback(async (obj) => {
        if (memoCluster.archType === T_K8S) {
            const targetId = obj?.version || obj?.id;

            const res = await axiosGet(`${API.getK8sInstanceValuesById}/${targetId}`);

            if (res.code === 200) {
                setTemplateData(res.data);
                originTemplateDataRef.current = res.data;
            }

            return res;
        }

        const params = {
            serviceInstanceId: instanceId,
            page: 1,
            pageSize: 10000,
            ...obj,
        };
        const res = await axiosPost(API.getConfigInfo, params);

        if (res.code === 200) {
            setTemplateData(invokeHandleTemplateData(res.data));
            originTemplateDataRef.current = res.data;
        }

        return res;
    }, [instanceId, memoCluster.archType]);

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

    const listSimpleK8sInstanceValuesByInstanceId = useCallback(async () => {


        const res = await axiosGet(`${API.listSimpleK8sInstanceValuesByInstanceId}/${instanceId}`);

        if (res.code === 200) {
            const options = res.data || []

            setVerSionList(options);

            if (options.length > 0) {
                const version = options[0];

                onVersionChange(version.id)
            }
        }

        return res;
    }, [instanceId, onVersionChange]);

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

        // console.log('saveParam', saveParam)
    }, [clusterId, currentId, getConfigVersion, obj.serviceName])




    const invokeInit = useCallback(async () => {
        if (memoCluster.archType === T_K8S) {
            await listSimpleK8sInstanceValuesByInstanceId();
        } else {
            await getServiceRoleType();
        }

        setHadInit(true)
    }, [listSimpleK8sInstanceValuesByInstanceId, getServiceRoleType, memoCluster.archType])


    const invokeContentRender = useCallback(() => {
        let res

        if (templateData && (memoCluster.archType === T_PHYSICAL || isEmpty(memoCluster.archType))) {
            res = (
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
            )
        } else if (memoCluster.archType === T_K8S) {
            // 

            const handleSave = async ({ record, middleEditorValue }) => {
                // if (typeof onConfirm === 'function') {
                //     try {
                //         await onConfirm({
                //             path: selectedPath,
                //             content: middleEditorValue,
                //         });
                //         message.success('保存成功');
                //     } catch (error) {
                //         console.error(error);
                //         message.error('保存失败');
                //     }
                // }
                const res = await axiosJsonPost(API.updateK8sInstanceValues, {
                    id: record.id,
                    deltaValues: middleEditorValue,
                })

                showMsgAfferRequest(res)


            }

            return (
                <Helm
                    record={templateData || {}}
                    handleSave={handleSave}
                />
            )
        }

        return res
    }, [memoCluster.archType, onFinish, templateData])

    useEffect(() => {
        invokeInit()
    }, [invokeInit])


    return (
        <div className="flex flex-1 min-h-[72vh] h-full w-full">
            {
                !hadInit ? <Spin className="w-full flex" /> :
                    <>
                        {
                            memoCluster.archType === T_PHYSICAL || isEmpty(memoCluster.archType) && (
                                <Menu
                                    className="w-[200px] h-full !border-none"
                                    items={memoRoleGroupList}
                                    selectedKeys={[currentId]}
                                    onClick={onMenuClick}
                                />
                            )
                        }

                        <div className="px-[20px] border-l border-[#f0f0f0] flex-1 flex flex-col">
                            <Space wrap className="!text-black mb-[20px]">
                                版本:
                                <Select
                                    className="w-[100px]"
                                    onChange={onVersionChange}
                                    options={versionOpts}
                                    value={currentVersion}
                                />
                            </Space>
                            {invokeContentRender()}

                        </div>
                    </>

            }





        </div>
    )
}


export default memo(Index)