import { pickControlPropsWithId, ProCard, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { API } from "../../../../../../../api";
import { forwardRef, use, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosJsonPost, axiosPost } from "../../../../../../../api/request";
import CommonTemplate from "../../../../../../../components/Common/CommonTemplate";
import { useConfigContext } from "../../configContext";
import { sm4Decrypt } from "../../../../../../../utils/secretUtils";
import * as yaml from 'js-yaml';



function deleteNum(str, key) {
    let reg = /[0-9]+/g;
    let str1 = str.replace(reg, "");
    let str2 = str1.replace(key, "");
    return str2;
}

const Index = ({
    current,
    formMapRef,
    record,
    index,
    steps4Data,

}, ref) => {
    const hosListRef = useRef([])
    const [templateData, setTemplateData] = useState([])
    const { clusterId } = useConfigContext()


    if (!steps4Data) {
        steps4Data = formMapRef.current[index - 1]?.current?.getFieldsValue() || {}
    }

    console.log('steps4Data', steps4Data)

    const handlerData = useCallback((data) => {
        const arr = [];
        data.map((item) => {
            arr.push({
                label: item.serviceRoleName,
                name: item.serviceRoleName,
                value: item.hosts ? item.hosts : hosListRef.current.length > 1 ? hosListRef.current[0] : undefined,
                defaultValue: item.hosts ? item.hosts : hosListRef.current.length > 1 ? hosListRef.current[0] : undefined,
                selectValue: hosListRef.current,
                type: item.cardinality === "1" ? "select" : "multipleSelect",
                isHidden: false,
                required: item.serviceRoleType === "master",
            });
        });
        return arr;
    }, [])



    const invokeInitYamlData = useCallback(async () => {

        const stepImportManifestRef = formMapRef.current[index - 2]

        const values = stepImportManifestRef?.current?.getFieldsValue() || {}
        const deployFileId = values.deployFileId
        const contentDecodePasswd = values.contentDecodePasswd
        if (deployFileId) {


            const yamlData = deployFileId && await new Promise((resolve) => {
                const file = deployFileId?.[0]?.originFileObj;
                const reader = new FileReader();

                reader.onload = function (event) {
                    const yamlText = event.target.result;

                    let content = yamlText


                    try {

                        // const keyBase64 = "E9+IV0ZpPTMKLzBnfeXPCQ==";
                        // const key = Buffer.from(keyBase64, 'base64').toString('hex'); // 转为 hex 字符串
                        content = sm4Decrypt(contentDecodePasswd, content)
                    } catch (error) {
                        console.warn('解密失败', error)
                    }

                    try {


                        content = yaml.load(content); // 使用 js-yaml 解析
                        console.log('loadcontent', content)
                        content = JSON.parse(JSON.stringify(content, null, 2));
                    } catch (err) {
                        console.warn('YAML 解析错误:', err);
                        content = undefined
                        // document.getElementById('output').textContent = '解析失败: ' + err.message;
                    }

                    resolve(content)
                };


                if (file) {

                    console.log('file', file)
                    reader.readAsText(file);

                }
            })

            return yamlData
        }


    }, [formMapRef, index])

    const getServiceRoleList = useCallback(async () => {


        // const invokeInitYamlDataRes = await invokeInitYamlData()
        // // if (invokeInitYamlDataRes?.app && false) {
        // const invokeInitYamlDataResMap = invokeInitYamlDataRes?.app?.reduce((pre, aft) => {

        //     aft.roles?.forEach(val => {
        //         pre[val.name] = val
        //     })

        //     return pre
        // }, {})

        // console.log('invokeInitYamlDataResMap', invokeInitYamlDataResMap)

        // } else {
        const params = {
            clusterId,
            serviceIds: steps4Data.services.map(val => val.id).join(",") || "",
            serviceRoleType: 1, // 传1查的是Master角色
        };
        const res = await axiosPost(API.getServiceRoleList, params)

        // res.data.map(val => {
        //     const deployHosts = invokeInitYamlDataResMap[val.serviceRoleName]?.deployHosts
        //     if (deployHosts?.length) {
        //         val.hosts = deployHosts
        //     }
        // })
        // }


        if (res.code === 200) {
            setTemplateData(handlerData(res.data))
        }
    }, [clusterId, handlerData, steps4Data.services])

    const getAllHost = useCallback(async () => {
        const params = {
            clusterId,
        };
        const res = await axiosPost(API.getAllHost, params)

        if (res.code === 200) {
            const arr = [];
            res.data.map((item) => {
                arr.push(item.hostname);
            });

            // TODO:测试
            // arr.push(1, 2, 3)
            // setHostList(arr)
            hosListRef.current = arr
        }


        await getServiceRoleList()


    }, [clusterId, getServiceRoleList])


    const invokeValid = useCallback(async () => {
        const fieldValue = formMapRef.current[index]?.current.getFieldsValue()
        console.log('fieldValue', fieldValue)
        let formData = {};

        let saveParam = [];

        for (const k in fieldValue) {
            const key = deleteNum(k, "multipleSelect");
            if (k.includes("multipleSelect")) {
                if (Object.prototype.hasOwnProperty.call(formData, key)) {
                    formData[`${key}`].push(fieldValue[k]);
                } else {
                    formData[`${key}`] = [fieldValue[k]];
                }
            } else {
                if (
                    Object.prototype.toString.call(fieldValue[k]) === "[object Array]"
                ) {
                    formData[`${k}`] = fieldValue[k];
                } else {
                    formData[`${k}`] = [fieldValue[k]];
                }
            }
        }

        for (const label in formData) {
            saveParam.push({
                serviceRole: label,
                hosts: formData[label],
            });
            templateData.forEach((item) => {
                if (item.label === label) {
                    item.value = formData[label];
                }
            });
        }


        const res = await axiosJsonPost(
            API.saveServiceRoleHostMapping + `/${clusterId}`,
            saveParam
        )

        return {
            valid: res.code === 200,
            msg: res.msg
        }

    }, [clusterId, formMapRef, index, templateData])






    useEffect(() => {
        if (current === index) {
            getAllHost()
        }
    }, [current, getAllHost, index])


    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })

    return (

        <ProCard
            bordered={true}
            // className="mb-[20px]"
            className="!mb-[20px]"
        >
            <CommonTemplate
                className="h-[50vh] overflow-auto w-full"
                templateData={templateData}
                steps4Data={steps4Data}
            />
        </ProCard>
    )
};

export default forwardRef(Index) 