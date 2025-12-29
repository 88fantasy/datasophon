import { pickControlPropsWithId, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { API } from "../../../../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosPost } from "../../../../../../../api/request";
import { useConfigContext } from "../../configContext";





const Index = ({
    current,
    formMapRef,
    record,
    index
}, ref) => {

    const actionRef = useRef()
    const [selectedRows, setSelectedRows] = useState([])
    const [dataSource, setDataSource] = useState([])
    const { clusterId } = useConfigContext()


    const invokeUpdateFormData = useCallback((arr, source) => {


        arr = arr.filter(
            (item) => item.installed
        );


        formMapRef.current[index]?.current.setFieldsValue({
            services: arr,
        })
    }, [formMapRef, index])



    const invokeInit = useCallback(async () => {
        const params = {
            clusterId,
        };

        const res = await axiosPost(API.getServiceList, params)

        if (res.code === 200) {
            res.data.map(val => {
                val.installed = true
            })
            setDataSource(res.data)
            const arr = res.data.filter(item => item.installed)
            if (arr.length > 0) {
                setSelectedRows(arr)

            }




            invokeUpdateFormData(arr, res.data)

            // TODO:对比源代码补充
        }

    }, [clusterId, invokeUpdateFormData])


    const invokeValid = useCallback(async () => {
        const fieldValue = formMapRef.current[index]?.current.getFieldsValue()
        const { services } = fieldValue


        if (!services?.length) {
            return {
                valid: false,
                msg: '请至少选择一个服务'
            }
        }

        const params = {
            clusterId,
            serviceIds: (services || []).map(val => val.id)
        };

        const res = await axiosPost(API.checkServiceDependency, params);


        // TODO: 测试

        // return {
        //     valid: true
        // }
        return {
            valid: res.code === 200,
            msg: res.msg
        }

    }, [clusterId, formMapRef, index])

    const columns: ProColumns[] = useMemo(() => {
        return [
            {
                dataIndex: 'index',
                title: '序号',
                valueType: 'indexBorder',
                width: 48,
            },
            {
                title: '服务',
                dataIndex: 'label',
                ellipsis: true,
            },
            {
                title: '描述',
                dataIndex: 'serviceDesc'
            },
            {
                title: '版本',
                dataIndex: 'serviceVersion',
                ellipsis: true,
                search: false,
            },
        ]
    }, []);





    useEffect(() => {
        if (
            current === index &&
            !dataSource?.length
        ) {
            invokeInit()
        }
    }, [current, dataSource?.length, index, invokeInit])


    useImperativeHandle(ref, () => {
        return {
            invokeValid
        }
    })

    return (
        <>
            {/* <ProFormItemRender
                name="services"
                className="w-[0] h-[0]"
            >


                {
                    (...args) => {
                        console.log('args', args, pickControlPropsWithId(args[0]))
                        return (
                            155454
                        )
                    }
                }

            </ProFormItemRender> */}
            <ProForm.Item
                name="services"
            >
                <CommonTable
                    tableProps={{
                        actionRef: actionRef,
                        search: false,
                        dataSource,
                        columns,
                        className: 'mb-[20px]',
                        manualRequest: true,
                        tableAlertRender: false,
                        scroll: {
                            y: '30vh'
                        },
                        rowSelection: {
                            selectedRowKeys: selectedRows.map(val => val.id),
                            onChange: (selectedRowKeys, selectedRows) => {
                                // console.log(selectedRowKeys, selectedRows);
                                setSelectedRows(selectedRows)

                                // currentFormRefCur.
                                console.log('selectedRows', selectedRows)

                                // currentFormRefCur.setFieldsValue({
                                //     sevices: {
                                //         serviceIds: selectedRowKeys,
                                //         serviceNames: selectedRows
                                //     }

                                // })

                                invokeUpdateFormData(selectedRows, dataSource)
                                // console.log('setFieldsValue', currentFormRefCur.getFieldsValue())
                            },

                        },
                    }
                    }

                />
            </ProForm.Item>

            {/* </ProForm.Item> */}
        </>
    )
};

export default forwardRef(Index) 