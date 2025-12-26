
import { API } from '../../../api';
import { invokePackProtableRequest } from '../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import { axiosPost, axiosPostUpload } from '../../../api/request';
import { useParams } from 'react-router';
import type { ProColumns } from '@ant-design/pro-components';
import { use, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { message, Tag } from 'antd';
import { cloneDeep, noop } from 'lodash-es';
import CommonBtnList from '../../../components/Common/CommonBtnList';
import { PlusOutlined } from '@ant-design/icons';
import { showComfirmModal, showMsgAfferRequest } from '../../../utils/util';
import asyncHook from '../../../components/Common/CommonModal/asyncHook';


const showFormModal = asyncHook(() =>
    import("./BuildOrEditModal/api"));




const Index = () => {

    const { clusterId } = useParams()

    const actionRef = useRef()


    const [groupList, setGroupList] = useState([])

    const [selectedRows, setSelectedRows] = useState([])

    const onBuildOrEditClick = useCallback(async ({
        action,
        record
    }) => {
        const modelApi = await showFormModal()

        modelApi.default({
            record,
            clusterId,
            onOk: () => {
                action?.reload?.()
            }
        })
    }, [clusterId])

    const getAlarmGroupList = useCallback(async () => {
        const params = {
            pageSize: 1000,
            page: 1,
            clusterId,
        };
        const res = await axiosPost(API.getAlarmGroupList, params)


        if (res.code === 200) {
            setGroupList(res.data)
        }


    }, [clusterId])



    const columns: ProColumns[] = useMemo(() => {
        return [
            {
                dataIndex: 'index',
                title: '序号',
                valueType: 'indexBorder',
                width: 48,
            },
            {
                title: '指标名称',
                dataIndex: 'alertQuotaName',
                ellipsis: true,
            },
            {
                title: '比较方式',
                dataIndex: 'compareMethod',
                search: false,
                ellipsis: true,
            },
            {
                title: '告警阀值',
                dataIndex: 'alertThreshold',
                ellipsis: true,
                search: false,
            },
            {
                title: '告警组',
                dataIndex: 'alertGroupName',
                ellipsis: true,
                valueEnum: groupList.reduce((acc, cur) => {
                    acc[cur.id] = cur.alertGroupName
                    return acc
                }, {})

            },
            {
                title: '通知组',
                dataIndex: 'noticeGroupId',
                ellipsis: true,
                search: false,
            },
            {
                title: '状态',
                dataIndex: 'quotaState',
                ellipsis: true,
                search: false,
                render: (text, record) => {

                    const colorMap = {
                        1: 'success',
                        2: 'error',
                    }

                    const color = colorMap[record.quotaStateCode] || 'default'

                    return <Tag color={color}>{record.quotaState}</Tag>

                }
            },
            {
                title: '操作',
                valueType: 'option',
                key: 'option',
                width: 200,
                render: invokeGenOptionCol([
                    {
                        title: '编辑',
                        onClick: (text, record) => {
                            onBuildOrEditClick({
                                action: actionRef.current,
                                record
                            })
                        }
                    },
                    {
                        title: '删除',
                        titleKey: 'alertGroupName',
                        onClick: async (text, record) => {

                            const params = JSON.stringify([record.id]);

                            return axiosPostUpload(API.deleteMetric, params)
                        }
                    },
                ])
            },
        ]
    }, [groupList, onBuildOrEditClick]);


    const onEnableOrDisableClick = useCallback(async (type, arr) => {
        if (!arr.length) {
            message.warning('请选择要操作的项')

            return
        }


        let res = await showComfirmModal({
            content: `确定要${type === 'enable' ? '启用' : '停用'}所选项吗？`,
        })

        if (res) {
            const params = {
                alertQuotaIds: selectedRows.map(val => val.id).join(","),
                clusterId
            };
            res = await axiosPost(
                type === 'enable' ? API.quotaStart : API.quotaStop,
                params
            )

            showMsgAfferRequest(res)

            setSelectedRows([])


            actionRef.current?.reload()
            // .then((res) => {
            //     if (res.code === 200) {
            //         this.$message.success("操作成功");
            //         this.$destroyAll();
            //         this.selectedRowKeys = [];
            //         this.onSearch();
            //     }
            // });
        }


    }, [clusterId, selectedRows])


    const toolBarRender = useCallback(() => {



        const list = [
            {
                label: '启用',
                onClick: onEnableOrDisableClick.bind(noop, 'enable', selectedRows)
            },
            {
                label: '停用',
                onClick: onEnableOrDisableClick.bind(noop, 'disable', selectedRows)
            },
            {
                label: '新建',
                type: 'primary',
                icon: <PlusOutlined />,
                onClick: onBuildOrEditClick.bind(noop, {
                    action: actionRef.current
                }),
            },

        ]
        return (
            <CommonBtnList
                list={list}
            />
        )
    }, [onBuildOrEditClick, onEnableOrDisableClick, selectedRows])

    useEffect(() => {
        getAlarmGroupList()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return (
        <CommonTable
            tableProps={{
                actionRef: actionRef,

                request: invokePackProtableRequest({
                    api: API.getAlarmMerticList,
                    params: (params) => {
                        params = cloneDeep(params)

                        const res = {
                            ...params,
                            quotaName: params.alertQuotaName || '',
                            alertGroupId: params.alertGroupName || '',
                            clusterId
                        }


                        delete params.alertQuotaName
                        delete params.alertGroupName
                        return res

                    }
                }),
                columns,
                tableAlertRender: false,
                toolBarRender,
                rowSelection: {
                    selectedRowKeys: selectedRows.map(val => val.id),
                    onChange: (k, arr) => {
                        setSelectedRows(arr)
                    }
                }
            }}
        />
    )
};

export default Index