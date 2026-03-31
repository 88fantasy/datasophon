import { ProCard, type ProColumns } from "@ant-design/pro-components";
import { API } from "../../../../../../../api";
import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosGet, axiosJsonPost, axiosPost } from "../../../../../../../api/request";
import { isEmpty, showMsgAfferRequest } from "../../../../../../../utils/util";
import { Checkbox, message, Tabs, Tag } from "antd";
import { useConfigContext } from "../../configContext";
import { clone, cloneDeep } from "lodash-es";
import { invokeFormatTemplateData, invokeHandleTemplateData } from "../../../../../../../components/Common/CommonTemplate/utils";
import CommonTemplate from "../../../../../../../components/Common/CommonTemplate";
import { T_K8S } from "../../../../../../../constants/clusterType";
import Helm from "../../../../../../ServiceManage/Instance/Setting/Helm";



// const columns: ProColumns[] = [
//     {
//         dataIndex: 'index',
//         title: '序号',
//         valueType: 'indexBorder',
//         width: 48,
//     },
//     {
//         title: '主机名',
//         dataIndex: 'hostname',
//         ellipsis: true,
//     },
// ];

const Index = ({
    current,
    formMapRef,
    record,
    index,
    steps4Data,
    memoCluster
}, ref) => {


    if (!steps4Data) {
        let pos = index - 3
        if (memoCluster.archType === T_K8S) {
            pos = index - 1
        }

        steps4Data = formMapRef.current[pos]?.current?.getFieldsValue() || {}

    }


    // const steps4Data = formMapRef.current[3]?.current?.getFieldsValue() || {}
    const helmRef = useRef()

    const currentFormRef = formMapRef.current[index]

    const templateMapRef = useRef({})
    const [hadInit, setHadInit] = useState(false)
    const [activeKey, setActiveKey] = useState()

    const { clusterId } = useConfigContext()


    const invokeMapMemoTab = useCallback(() => {
        return steps4Data.services?.map(val => {
            let key
            let label

            let children

            if (memoCluster.archType === T_K8S) {
                label = val.serviceName
                key = val.id
                children = (
                    <div className="h-[40vh] flex flex-col">
                        <Helm
                            record={templateMapRef.current[val.id]}
                            ref={helmRef}
                        />
                    </div>
                )
            } else {
                key = val.serviceName
                children = <CommonTemplate
                    namePrefix={[key]}
                    templateData={templateMapRef.current[key] || []}
                />
            }
            return {
                key,
                label: label || key,
                value: key,
                originData: val,
                children,
            }
        })
    }, [memoCluster.archType, steps4Data.services])

    const memoTabs = invokeMapMemoTab()

    const getServiceConfigOption = useCallback(async () => {

        const reqArr = await Promise.all(
            memoTabs.map((item) => {
                if (memoCluster.archType === T_K8S) {
                    return axiosGet(`${API.getValueFromRepo}/${item.originData?.id}`, {
                        artifactType: item.originData?.metaFileType
                    }).then(
                        (res) => {
                            if (res.code === 200) {

                                return {
                                    key: item.originData?.id,
                                    data: res.data,
                                }
                            }
                        }
                    );
                } else {
                    const params = {
                        clusterId,
                        serviceName: item.label,
                    };
                    return axiosPost(API.getServiceConfigFromDdl, params)
                        .then(
                            (res) => {
                                if (res.code === 200) {

                                    return {
                                        key: item.key,
                                        data: invokeHandleTemplateData(res.data),
                                    }
                                }
                            }
                        );
                }

            })
        );


        const res = reqArr.reduce((a, b) => {
            a[b.key] = b.data
            return a
        }, {})

        // setTemplateMap(res)
        console.log('templateMapRef', templateMapRef.current)

        templateMapRef.current = res
    }, [clusterId, memoCluster.archType, memoTabs])


    const invokeGenerateGenericInstallCommand = useCallback(async () => {
        let params = {
            clusterId,
            serviceNames: memoTabs.map(val => val.label),
            //TODO:
            commandType: 'INSTALL_SERVICE'
        };


        const generateCommandRes = await axiosPost(API.generateGenericInstallCommand, params)

        if (generateCommandRes.code === 200) {
            // params.commandIds = generateCommandRes.data

            // delete params.servicenames;
            params = {
                dagId: generateCommandRes.data
            }

            const startExecuteCommandRes = await axiosJsonPost(API.redeploy, params)

            return {
                valid: startExecuteCommandRes.code === 200,
                msg: startExecuteCommandRes.msg
            }
        } else {
            return {
                valid: false,
                msg: generateCommandRes.msg
            }
        }
    }, [clusterId, memoTabs])


    const invokeSaveAsCommon = useCallback(async () => {
        const cpTemplateMap = cloneDeep(templateMapRef.current)

        const values = {}
        Object.keys(cpTemplateMap)
            .forEach((k) => {

                if (!values[k]) {
                    values[k] = {}
                }

                cpTemplateMap[k].map(v => {
                    values[k][v.name] = isEmpty(v.value) ? v.defaultValue : v.value
                })

            })




        const currentValue = currentFormRef.current?.getFieldsValue()

        let res


        Object.assign(values, currentValue)

        for (const tab of memoTabs) {
            const serviceValue = values[tab.key]
            const serviceTemplate = templateMapRef.current[tab.key]
            for (const item of serviceTemplate) {
                if (
                    !item.hidden &&
                    item.required &&
                    // isEmpty(serviceValue?.[item.name])
                    isEmpty(item.value) &&
                    isEmpty(item.defaultValue) &&
                    isEmpty(serviceValue[item.name])

                ) {
                    res = `${tab.key}.${item.label} 不能为空`
                }

                if (res) {

                    break
                }
            }

            if (res) {
                break
            }

            values[tab.key] = invokeFormatTemplateData(serviceTemplate, serviceValue)
        }

        if (res) {
            return {
                valid: false,
                msg: res
            }
        } else {
            const reqArr = await Promise.all(
                memoTabs.map(tab => {



                    const saveParam = {
                        clusterId: clusterId,
                        serviceName: tab.key,
                        serviceConfig: JSON.stringify(values[tab.key]),
                        roleGroupId: "-1"
                    };
                    return axiosPost(
                        API.saveServiceConfig,
                        saveParam
                    )
                })
            )


            for (const item of reqArr) {
                if (item.code !== 200) {
                    res = item.msg
                    break
                }

            }

            if (res) {
                return {
                    valid: false,
                    msg: res
                }
            }

            return invokeGenerateGenericInstallCommand()

        }
    }, [clusterId, currentFormRef, invokeGenerateGenericInstallCommand, memoTabs])


    const invokeSaveAsK8s = useCallback(async () => {
        const cpTemplateMap = cloneDeep(templateMapRef.current)

        const currentValue = helmRef.current?.middleEditorValue

        let key = activeKey

        if (!key) {
            key = memoTabs[0].key
        }

        cpTemplateMap[key].deltaValues = currentValue

        let res


        const reqArr = await Promise.all(
            memoTabs.map(tab => {



                const saveParam = {
                    clusterId,
                    serviceId: tab.key,
                    namespace: tab.originData.namespace,
                    metaFileType: tab.originData.metaFileType,
                    ...(cpTemplateMap[tab.key] || {}),
                };
                return axiosJsonPost(
                    API.saveConfigValuesK8s,
                    saveParam
                )
            })
        )


        for (const item of reqArr) {
            if (item.code !== 200) {
                res = item.msg
                break
            }

        }

        if (res) {
            return {
                valid: false,
                msg: res
            }
        } else {
            return invokeGenerateGenericInstallCommand()
        }



    }, [activeKey, clusterId, invokeGenerateGenericInstallCommand, memoTabs])

    const invokeValid = useCallback(async () => {


        if (memoCluster.archType === T_K8S) {
            return await invokeSaveAsK8s()
        } else {
            return await invokeSaveAsCommon()
        }




        // const params = {
        //     clusterId,
        // };

        // const res = await axiosPost(API.hostCheckCompleted, params);

        // return res

    }, [invokeSaveAsCommon, invokeSaveAsK8s, memoCluster.archType])

    const onTabChange = useCallback(async (key) => {
        setActiveKey(key)
    }, [])


    const invokeInit = useCallback(async () => {

        if (current === index) {
            await getServiceConfigOption()

            setHadInit(true)
            // currentRef.current = true
        }
    }, [current, getServiceConfigOption, index])

    useEffect(() => {
        invokeInit()
    }, [invokeInit])



    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })

    return hadInit && (
        <ProCard
            bordered={true}
            className="h-[50vh] overflow-auto !mb-[20px]"
        >
            <>
                <Tabs activeKey={activeKey} items={memoTabs} onChange={onTabChange} />

            </>
        </ProCard>
    )
};

export default forwardRef(Index) 