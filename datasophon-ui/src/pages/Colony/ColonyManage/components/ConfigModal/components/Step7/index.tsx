import { ProCard, type ProColumns } from "@ant-design/pro-components";
import type { GithubIssueItem } from "../../../../../../../components/Common/CommonTable";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { invokePackProtableRequest } from "../../../../../../../utils/request";
import { API } from "../../../../../../../api";
import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { invokeMapValue } from "../../../../../../../utils/listUtils";
import { axiosJsonPost, axiosPost } from "../../../../../../../api/request";
import { isEmpty, showMsgAfferRequest } from "../../../../../../../utils/util";
import CommonBtnList from "../../../../../../../components/Common/CommonBtnList";
import { Checkbox, message, Tabs, Tag } from "antd";
import { useConfigContext } from "../../configContext";
import { clone, cloneDeep } from "lodash-es";
import { invokeFormatTemplateData, invokeHandleTemplateData } from "../../../../../../../components/Common/CommonTemplate/utils";
import CommonTemplate from "../../../../../../../components/Common/CommonTemplate";



const columns: ProColumns[] = [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '主机名',
        dataIndex: 'hostname',
        ellipsis: true,
    },
];

const Index = ({
    current,
    formMapRef,
    record
}, ref) => {

    const steps4Data = formMapRef.current[3]?.current?.getFieldsValue() || {}

    const currentFormRef = formMapRef.current[current]

    const [hadInit, setHadInit] = useState(false)
    const [dataSource, setDataSource] = useState([])
    const [activeKey, setActiveKey] = useState()


    const [templateMap, setTemplateMap] = useState({})
    const { clusterId } = useConfigContext()


    const memoTabs = steps4Data.services?.map(val => {
        const key = val.serviceName
        return {
            key,
            label: key,
            children: <CommonTemplate
                namePrefix={[key]}
                templateData={templateMap[key]}
            />
        }
    })

    const getServiceConfigOption = async () => {

        const reqArr = await Promise.all(
            memoTabs.map((item) => {
                const params = {
                    clusterId,
                    serviceName: item.label,
                };
                return axiosPost(API.getServiceConfigOption, params)
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
            })
        );


        const res = reqArr.reduce((a, b) => {
            a[b.key] = b.data
            return a
        }, {})

        setTemplateMap(res)
    }



    const invokeValid = useCallback(async () => {


        const values = currentFormRef.current?.getFieldsValue()

        let res

        for (const tab of memoTabs) {
            const serviceValue = values[tab.key]
            const serviceTemplate = templateMap[tab.key]


            for (const item of serviceTemplate) {
                if (!item.hidden && item.required && isEmpty(serviceValue?.[item.name])) {
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



            const params = {
                clusterId,
                serviceNames: memoTabs.map(val => val.serviceName),
                //TODO:
                commandType: 'INSTALL_SERVICE'
            };


            const generateCommandRes = await axiosPost(API.generateCommand, params)

            if (generateCommandRes.code === 200) {
                params.commandIds = generateCommandRes.data

                delete params.servicenames;
                const startExecuteCommandRes = await axiosPost(API.startExecuteCommand, params)

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

        }


        // const params = {
        //     clusterId,
        // };

        // const res = await axiosPost(API.hostCheckCompleted, params);

        // return res

    }, [dataSource])

    const onTabChange = async (key) => {
        setActiveKey(key)
    }

    const invokeInit = useCallback(async () => {
        if (current === 6) {
            await getServiceConfigOption()
            setHadInit(true)
        }
    }, [current])

    useEffect(() => {
        invokeInit()
    }, [invokeInit])

    useEffect(() => {
        if (current === 6) {
            setActiveKey(memoTabs[0]?.key)
        }
    }, [current])


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