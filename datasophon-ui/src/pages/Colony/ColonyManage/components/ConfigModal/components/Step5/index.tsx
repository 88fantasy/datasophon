import { pickControlPropsWithId, ProCard, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { API } from "../../../../../../../api";
import { forwardRef, use, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosJsonPost, axiosPost } from "../../../../../../../api/request";
import CommonTemplate from "../../../../../../../components/Common/CommonTemplate";
import { useConfigContext } from "../../configContext";



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

}, ref) => {
    // const [hostList, setHostList] = useState([])
    const hosListRef = useRef([])
    const [templateData, setTemplateData] = useState([])
    const [hadInit, setHadInit] = useState()
    const { clusterId } = useConfigContext()

    const steps4Data = formMapRef.current[3]?.current?.getFieldsValue() || {}


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

    const getServiceRoleList = useCallback(async () => {
        const params = {
            clusterId,
            serviceIds: steps4Data.services.map(val => val.id).join(",") || "",
            serviceRoleType: 1, // 传1查的是Master角色
        };
        const res = await axiosPost(API.getServiceRoleList, params)

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
        const fieldValue = formMapRef.current[4]?.current.getFieldsValue()
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

    }, [clusterId, formMapRef, templateData])






    useEffect(() => {
        if (current === 4) {
            getAllHost()
        }
    }, [current, getAllHost])


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