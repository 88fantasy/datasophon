import { pickControlPropsWithId, ProCard, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { API } from "../../../../../../../api";
import { forwardRef, use, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosPost } from "../../../../../../../api/request";
import CommonTemplate from "../../../../../../../components/Common/CommonTemplate";
import { useConfigContext } from "../../configContext";





const Index = ({
    current,
    formMapRef,
    record,

}, ref) => {
    const [hostList, setHostList] = useState([])
    const [templateData, setTemplateData] = useState([])
    const { clusterId } = useConfigContext()

    const steps4Data = formMapRef.current[3]?.current?.getFieldsValue() || {}


    const handlerData = (data) => {
        const arr = [];
        data.map((item) => {
            arr.push({
                label: item.serviceRoleName,
                name: item.serviceRoleName,
                value: item.hosts ? item.hosts : hostList.length > 1 ? hostList[0] : undefined,
                defaultValue: item.hosts ? item.hosts : hostList.length > 1 ? hostList[0] : undefined,
                selectValue: hostList,
                type: item.cardinality === "1" ? "select" : "multipleSelect",
                isHidden: false,
                required: item.serviceRoleType === "master",
            });
        });
        return arr;
    }
    const getServiceRoleList = async () => {
        const params = {
            clusterId,
            serviceIds: steps4Data.services.map(val => val.id).join(",") || "",
            serviceRoleType: 1, // 传1查的是Master角色
        };
        const res = await axiosPost(API.getServiceRoleList, params)

        if (res.code === 200) {
            setTemplateData(handlerData(res.data))
        }
    }

    const getAllHost = async () => {
        const params = {
            clusterId,
        };
        const res = await axiosPost(API.getAllHost, params)

        if (res.code === 200) {
            const arr = [];
            res.data.map((item) => {
                arr.push(item.hostname);
            });

            setHostList(arr)
        }


        await getServiceRoleList()
    }


    const invokeValid = async () => {
        const fieldValue = formMapRef.current[3]?.current.getFieldsValue()
        const { services } = fieldValue


        if (!services?.length) {
            return {
                valid: false
            }
        }

        const params = {
            clusterId,
            serviceIds: (services || []).map(val => val.id)
        };

        const res = await axiosPost(API.checkServiceDependency, params);

        return {
            valid: res.code === 200,
            msg: res.msg
        }

    }






    useEffect(() => {
        if (current === 4) {
            getAllHost()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [current])


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