import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Tabs } from 'antd';
import { axiosGet, axiosJsonPost, axiosPost, axiosPostUpload } from '../../../api/request';
import { API } from '../../../api';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import type { ProColumns } from '@ant-design/pro-components';
import { invokeGenerateElId, showMsgAfferRequest } from '../../../utils/util';
import CommonTabs from '../../../components/Common/CommonTabs';
import asyncHook from '../../../components/Common/CommonModal/asyncHook';
import { mapEmptyValueFn } from '../../../utils/listUtils';
import TableProxy from './TableProxy';



const showUploadDeployModal = asyncHook(() => import('../../../components/UploadDeployModal/api'))

const showCommonLogModal = asyncHook(() =>
    import("../../../components/Common/CommonLogModal/api"))




const Index: React.FC = () => {


    const [state, setState] = useState()

    const [key, setKey] = useState(invokeGenerateElId())

    // const navigate = useNavigate()

    // const activeKey = getRouteQuery('tab')

    const invokeInit = useCallback(async () => {
        const res = await axiosPost(API.getFrameList, {})

        if (res.code === 200) {
            setState(res.data || [])
        }
    }, [])

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
                dataIndex: 'serviceName',
                search: false,
                ellipsis: true,
            },
            {
                title: '版本',
                dataIndex: 'serviceVersion',
                search: false,
                ellipsis: true,
            },
            {
                title: '描述',
                dataIndex: 'serviceDesc',
                ellipsis: true,
                search: false,
            },

            {
                title: '操作',
                valueType: 'option',
                key: 'option',
                width: 120,
                render: invokeGenOptionCol([
                    {

                        title: '删除',
                        titleKey: 'serviceName',
                        onClick: async (text, record) => {

                            const res = await axiosGet(API.deleteService + "/" + record.id)


                            if (res.code === 200) {
                                await invokeInit()
                                setKey(invokeGenerateElId())
                            }

                            return res
                        }
                    },
                    {

                        title: '编辑',
                        onClick: async (text, record) => {
                            const modelApi = await showCommonLogModal()

                            modelApi.default({
                                language: 'json',
                                options: {
                                    readOnly: false
                                },
                                api: () => {
                                    return axiosGet(`${API.getServiceDdl}/${record.id}`)
                                },
                                onOk: async (content) => {
                                    const res = await axiosJsonPost(`${API.updateDdl2}/${record.id}`, {
                                        content
                                    })


                                    showMsgAfferRequest(res)


                                    return res.code === 200
                                }
                            })

                        }
                    },
                ])
            },
        ];
    }, [invokeInit])

    const k8sColumns: ProColumns[] = useMemo(() => {
        return [
            {
                dataIndex: 'index',
                title: '序号',
                valueType: 'indexBorder',
                width: 48,
            },
            {
                title: '服务',
                dataIndex: 'serviceName',
                search: false,
                ellipsis: true,
            },
            {
                title: '版本',
                dataIndex: 'serviceVersion',
                search: false,
                ellipsis: true,
            },
            {
                title: '描述',
                dataIndex: 'serviceDesc',
                ellipsis: true,
                search: false,
            },
            {
                title: '依赖',
                dataIndex: 'dependencies',
                ellipsis: true,
                search: false,
                render: (text, record) => {
                    if (Array.isArray(record.dependencies)) {
                        text = record.dependencies.join(',')
                    }

                    return mapEmptyValueFn(text)
                },
            },
            {
                title: '制品信息',
                dataIndex: 'artifact',
                ellipsis: true,
                search: false,
            },
            {
                title: '支持的制品类型',
                dataIndex: 'supportArtifacts',
                ellipsis: true,
                search: false,
                render: (text, record) => {
                    if (Array.isArray(record.supportArtifacts)) {
                        text = record.supportArtifacts.join(',')
                    }

                    return mapEmptyValueFn(text)
                },
            },
            {
                title: '定义的内容',
                dataIndex: 'manifestJson',
                ellipsis: true,
                search: false,
            },
            {
                title: '操作',
                valueType: 'option',
                key: 'option',
                width: 120,
                render: invokeGenOptionCol([
                    {

                        title: '删除',
                        titleKey: 'serviceName',
                        onClick: async (text, record) => {

                            const res = await axiosGet(API.deleteService + "/" + record.id)


                            if (res.code === 200) {
                                await invokeInit()
                                setKey(invokeGenerateElId())
                            }

                            return res
                        }
                    }
                ])
            },
        ];
    }, [invokeInit])


    const memoTabItem = useMemo(() => {
        return (state || []).map((item) => {
            return {
                key: String(item.id),
                label: item.frameCode,
                children: (




                    <TableProxy
                        {
                        ...item
                        }
                        columns={columns}
                        k8sColumns={k8sColumns}
                    />
                )
            }
        })
    }, [columns, k8sColumns, state])

    // const onChange = (e) => {
    //     replaceRouter({
    //         navigate,
    //         query: {
    //             tab: e
    //         }
    //     })
    // }
    const tabBarExtraContent = useMemo(() => {


        const onImportClick = async () => {
            const modelApi = await showUploadDeployModal()
            modelApi.default({
                type: 'frame',
                onOk: () => {
                    invokeInit()
                    // setKey(invokeGenerateElId())
                }
            })
        }
        return {
            right: (
                <Button
                    variant="filled"
                    color="primary"
                    onClick={onImportClick}
                >
                    导入
                </Button>
            )
        }
    }, [invokeInit])


    useEffect(() => {
        invokeInit()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return state && (
        <CommonTabs
            bindUrl={true}
            memoTabItem={memoTabItem}
            tabBarExtraContent={tabBarExtraContent}
            key={key}
        />
    )
}

export default Index;