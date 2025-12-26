import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Tabs } from 'antd';
import { axiosGet, axiosPost, axiosPostUpload } from '../../../api/request';
import { API } from '../../../api';
import { useNavigate } from 'react-router-dom';
import { getRouteQuery, replaceRouter } from '../../../utils/routerUtils';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../components/Common/CommonTable';
import type { ProColumns } from '@ant-design/pro-components';
import { invokeGenerateElId } from '../../../utils/util';
import CommonTabs from '../../../components/Common/CommonTabs';
import asyncHook from '../../../components/Common/CommonModal/asyncHook';



const showUploadDeployModal = asyncHook(() => import('../../../components/UploadDeployModal/api'))



const Index: React.FC = () => {


    const [state, setState] = useState()

    const [key, setKey] = useState(invokeGenerateElId())

    // const navigate = useNavigate()

    // const activeKey = getRouteQuery('tab')

    const invokeInit = useCallback(async () => {
        const res = await axiosPost(API.getFrameList, {})

        if (res.code === 200) {
            setState(res.data)
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
                    <CommonTable
                        tableProps={{
                            search: false,
                            request: async (params, sort, filter) => {
                                // console.log(params, sort, filter);
                                const data = item.frameServiceList || []
                                return {
                                    data,
                                    total: data.length
                                }
                            },
                            columns,
                        }}
                    />
                )
            }
        })
    }, [columns, state])

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
        // <Tabs
        //     key={key}
        //     activeKey={activeKey || memoTabItem[0]?.key}
        //     items={memoTabItem}
        //     onChange={onChange}
        // />
        <CommonTabs
            memoTabItem={memoTabItem}
            tabBarExtraContent={tabBarExtraContent}
            key={key}
        />
    )
}

export default Index;