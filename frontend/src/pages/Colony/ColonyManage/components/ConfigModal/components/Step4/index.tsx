import { pickControlPropsWithId, ProForm, ProFormItemRender, type ProColumns } from "@ant-design/pro-components";
import CommonTable, { invokeGenOptionCol } from "../../../../../../../components/Common/CommonTable";
import { API } from "../../../../../../../api";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { axiosPost } from "../../../../../../../api/request";
import { Input } from "antd";





const Index = ({
    current,
    formMapRef,
    record,

}, ref) => {

    const actionRef = useRef()
    const [selectedRows, setSelectedRows] = useState([])
    const [dataSource, setDataSource] = useState([])

    const invokeUpdateFormData = useCallback((arr, source) => {


        arr = arr.filter(
            (item) => item.installed
        );


        formMapRef.current[3]?.current.setFieldsValue({
            services: arr,
        })
    }, [formMapRef])



    const invokeInit = async () => {
        const params = {
            clusterId: record.id,
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
            clusterId: record.id,
            serviceIds: (services || []).map(val => val.id)
        };

        const res = await axiosPost(API.checkServiceDependency, params);

        return {
            valid: res.code === 200,
            msg: res.msg
        }

    }

    const columns: ProColumns[] = [
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
    ];





    useEffect(() => {
        if (current === 3 && !dataSource?.length) {
            invokeInit()
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [current, dataSource?.length,])


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