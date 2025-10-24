import type { ProColumns } from "@ant-design/pro-components";
import type { GithubIssueItem } from "../../../../../../../components/Common/CommonTable";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { invokePackProtableRequest } from "../../../../../../../utils/request";
import { API } from "../../../../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { invokeMapValue } from "../../../../../../../utils/listUtils";
import { axiosJsonPost, axiosPost } from "../../../../../../../api/request";
import { showMsgAfferRequest } from "../../../../../../../utils/util";
import CommonBtnList from "../../../../../../../components/Common/CommonBtnList";
import { Checkbox, message, Tag } from "antd";
import { useConfigContext } from "../../configContext";
import { clone, cloneDeep } from "lodash-es";



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

    const actionRef = useRef()
    const workNameList = useRef()
    const tableHeaderData = useRef([])
    const [hadInit, setHadInit] = useState(false)
    const [dataSource, setDataSource] = useState([])
    const { clusterId } = useConfigContext()


    console.log('formMapRef', formMapRef)

    const steps4Data = formMapRef.current[3]?.current?.getFieldsValue() || {}



    const invokeValid = useCallback(async () => {

        let formData = {};
        let saveParam = [];
        (workNameList.current || []).map(item => {
            formData[`${item}`] = []
            dataSource.map(childItem => {
                if (childItem.checkedList.includes(item)) {
                    formData[`${item}`].push(childItem.hostname)
                }
            })
        })
        for (var label in formData) {
            saveParam.push({
                serviceRole: label,
                hosts: formData[label],
            });
        }

        const res = await axiosJsonPost(
            API.saveServiceRoleHostMapping + `/${clusterId}`,
            saveParam
        );

        // TODO:测试

        // return {
        //     valid: true
        // }

        return {
            valid: res.code === 200,
            msg: res.msg,
        }
        // const params = {
        //     clusterId,
        // };

        // const res = await axiosPost(API.hostCheckCompleted, params);

        // return res

    }, [dataSource])

    const changeheaderHost = useCallback((key) => {

        setDataSource((preState) => {
            let num = 0;
            preState.map((item) => {
                if (item[`${key}`]) num++;
            });
            preState.forEach((item) => {
                const hostIndex = item.checkedList.findIndex((item) => item === key);
                // 没有全选的时候，让他全选
                if (num < preState.length) {
                    if (hostIndex === -1) {
                        item.checkedList.push(key);
                    }
                    item[`${key}`] = true;
                } else {
                    // 取消取消操作
                    if (hostIndex !== -1) {
                        item.checkedList.splice(hostIndex, 1);
                    }
                    item[`${key}`] = false;
                }
            });

            return clone(preState)
        })

    }, [])

    const getAllCheckedStatus = useCallback((key) => {
        let num = 0;
        dataSource.map((item) => {
            if (item[`${key}`]) num++;
        });
        return num === dataSource.length;
    }, [dataSource])

    const getCheckedStatus = useCallback((key) => {
        let num = 0;
        dataSource.map((item) => {
            if (item[`${key}`]) num++;
        });
        return num > 0 && num < dataSource.length;
    }, [dataSource])

    const invokeGenCol = useCallback(() => {



        return [].concat(columns, tableHeaderData.current.map(item => {
            return {
                title: (text, row, index, actionRef) => {
                    return (
                        <Checkbox
                            onChange={() => changeheaderHost(item.serviceRoleName)}
                            checked={getAllCheckedStatus(item.serviceRoleName)}
                            indeterminate={getCheckedStatus(item.serviceRoleName)}
                        >
                            {item.serviceRoleName}
                        </Checkbox>

                    );
                },
                dataIndex: item.serviceRoleName,
                render: (text, row, index, actionRef) => {

                    const onChange = () => {

                        setDataSource((preState) => {
                            const obj = preState[index]

                            obj[item.serviceRoleName] = !obj[item.serviceRoleName]

                            return clone(preState)

                        })
                    }

                    return (
                        <Checkbox
                            checked={row[item.serviceRoleName]}
                            onChange={onChange}
                        />
                    );
                },
            }
        }))
    }, [changeheaderHost, getAllCheckedStatus, getCheckedStatus])

    const getNonMasterRoleList = async () => {
        const params = {
            clusterId: clusterId,
            serviceIds: steps4Data.services?.map(val => val.id).join(",") || "",
        };
        const res = await axiosPost(API.getNonMasterRoleList, params)


        if (res.code === 200) {

            let arr = [];
            res.data.map((item) => {
                arr.push(item.serviceRoleName);
                // //TODO: 测试
                // item.hosts = ['test', 'test1']
            });
            workNameList.current = arr;
            tableHeaderData.current = res.data;
        }

        return res
    }

    const apiFn = useCallback(async (params, sort, filter) => {
        const res = await axiosPost(API.getAllHost, {
            ...params,
            clusterId
        })

        const resArr = []
        if (res.code === 200) {
            //TODO: 测试
            // res.data = [
            //     {
            //         hostname: "test",
            //         id: 1,
            //         hosts: [
            //             "test"
            //         ]
            //     },
            //     {
            //         hostname: "test1",
            //         id: 1,
            //         hosts: [
            //             "test"
            //         ]
            //     }
            // ]
            res.data.map((item) => {
                let obj = {};
                tableHeaderData.current.map((keyItem) => {
                    let flag = keyItem.hosts.includes(item.hostname)
                    obj[`${keyItem.serviceRoleName}`] = flag;
                    if (flag) obj.checkedList = [keyItem.serviceRoleName]
                });
                resArr.push({
                    isChildSelected: false,
                    isAllSelected: false,
                    checkedList: [],
                    // DataNode: true,
                    hostname: item.hostname,
                    id: item.id,
                    ...obj,
                });
            })
        }

        setDataSource(resArr)
    }, [])

    const invokeInit = useCallback(async () => {
        if (current === 5) {
            await getNonMasterRoleList()
            setHadInit(true)
        }
    }, [current])

    useEffect(() => {
        console.log('current', current)
        invokeInit()
    }, [invokeInit])


    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })

    return hadInit && (
        <CommonTable
            tableProps={{
                actionRef: actionRef,
                search: false,
                request: apiFn,
                dataSource,
                columns: invokeGenCol(),
                className: 'mb-[20px]',
                tableAlertRender: false,
            }}

        />
    )
};

export default forwardRef(Index) 