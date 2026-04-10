import { API } from '../../../../api';
import { invokePackProtableRequest } from '../../../../utils/request';
import CommonTable, { invokeGenOptionCol, type GithubIssueItem } from '../../../../components/Common/CommonTable';
import { axiosJsonPost, axiosPost } from '../../../../api/request';
import { noop } from 'lodash-es';
import { useParams } from 'react-router';
import type { ProColumns } from '@ant-design/pro-components';
import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { Empty, Tag } from 'antd';
import CommonBtnList from '../../../../components/Common/CommonBtnList';
import { PlusOutlined } from '@ant-design/icons';
import { showMsgAfferRequest } from '../../../../utils/util';
import resourceType, { T_POD, T_SERVICE, T_DEPLOYMENT, T_INGRESS, T_CONFIGMAP, getResourceTypeLabelByValue } from '../../../../constants/resourceType';
import { getRouteQuery } from '../../../../utils/routerUtils';

interface K8sTableProps {
    resourceType: string;
}

// POD 类型列定义
const getPodColumns = (): ProColumns[] => [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '名称',
        dataIndex: 'name',
        ellipsis: true,
    },
    {
        title: '状态',
        dataIndex: 'status',
        ellipsis: true,
        render: (text, record) => {
            const statusColor = {
                'Running': 'green',
                'Pending': 'orange',
                'Failed': 'red',
                'Succeeded': 'blue',
                'Unknown': 'default',
            };
            return <Tag color={statusColor[text] || 'default'}>{text}</Tag>;
        },
    },
    {
        title: '创建时间',
        dataIndex: 'age',
        ellipsis: true,
    },
    {
        title: '就绪',
        dataIndex: 'ready',
        ellipsis: true,
    },
    {
        title: '重启次数',
        dataIndex: 'restartCount',
        ellipsis: true,
    },
    {
        title: '节点名称',
        dataIndex: 'nodeName',
        ellipsis: true,
    },
    {
        title: 'Pod IP',
        dataIndex: 'podIP',
        ellipsis: true,
    },
    // {
    //     title: '操作',
    //     valueType: 'option',
    //     key: 'option',
    //     width: 120,
    //     render: invokeGenOptionCol([
    //         {
    //             title: '查看',
    //             onClick: async (text, record, _, action) => {
    //                 // TODO: 处理查看操作
    //                 console.log('查看Pod:', record);
    //             }
    //         },
    //     ])
    // },
];

// SERVICE 类型列定义
const getServiceColumns = (): ProColumns[] => [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '名称',
        dataIndex: 'name',
        ellipsis: true,
    },
    {
        title: '命名空间',
        dataIndex: 'namespace',
        ellipsis: true,
    },
    {
        title: '创建时间',
        dataIndex: 'age',
        ellipsis: true,
    },
    {
        title: '服务类型',
        dataIndex: 'type',
        ellipsis: true,
        render: (text) => <Tag color="blue">{text}</Tag>,
    },
    {
        title: '集群 IP',
        dataIndex: 'clusterIP',
        ellipsis: true,
    },
    {
        title: '外部 IP',
        dataIndex: 'externalIP',
        ellipsis: true,
    },
    {
        title: '外部负载均衡 IP',
        dataIndex: 'loadBalancerIP',
        ellipsis: true,
    },
    {
        title: '选择器标签',
        dataIndex: 'selector',
        ellipsis: true,
    },
    {
        title: '会话粘性',
        dataIndex: 'sessionAffinity',
        ellipsis: true,
    },
    {
        title: '状态',
        dataIndex: 'status',
        ellipsis: true,
        render: (text) => <Tag color="green">{text}</Tag>,
    },
    // {
    //     title: '操作',
    //     valueType: 'option',
    //     key: 'option',
    //     width: 120,
    //     render: invokeGenOptionCol([
    //         {
    //             title: '查看',
    //             onClick: async (text, record, _, action) => {
    //                 console.log('查看Service:', record);
    //             }
    //         },
    //     ])
    // },
];

// DEPLOYMENT 类型列定义
const getDeploymentColumns = (): ProColumns[] => [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '名称',
        dataIndex: 'name',
        ellipsis: true,
    },
    {
        title: '命名空间',
        dataIndex: 'namespace',
        ellipsis: true,
    },
    {
        title: '创建时间',
        dataIndex: 'age',
        ellipsis: true,
    },
    {
        title: '就绪副本数',
        dataIndex: 'readyReplicas',
        ellipsis: true,
    },
    {
        title: '期望副本数',
        dataIndex: 'replicas',
        ellipsis: true,
    },
    {
        title: '可用副本数',
        dataIndex: 'availableReplicas',
        ellipsis: true,
    },
    {
        title: '未就绪副本数',
        dataIndex: 'unavailableReplicas',
        ellipsis: true,
    },
    {
        title: '状态',
        dataIndex: 'status',
        ellipsis: true,
        render: (text) => {
            const statusColor = {
                'Ready': 'green',
                'Progressing': 'orange',
                'Failed': 'red',
            };
            return <Tag color={statusColor[text] || 'default'}>{text}</Tag>;
        },
    },
    {
        title: '镜像',
        dataIndex: 'images',
        ellipsis: true,
        render: (images) => {
            return Array.isArray(images) ? images.join(', ') : images;
        },
    },
    {
        title: '选择器标签',
        dataIndex: 'selector',
        ellipsis: true,
    },
    {
        title: '更新策略',
        dataIndex: 'strategy',
        ellipsis: true,
    },
    // {
    //     title: '操作',
    //     valueType: 'option',
    //     key: 'option',
    //     width: 120,
    //     render: invokeGenOptionCol([
    //         {
    //             title: '查看',
    //             onClick: async (text, record, _, action) => {
    //                 console.log('查看Deployment:', record);
    //             }
    //         },
    //     ])
    // },
];

// INGRESS 类型列定义
const getIngressColumns = (): ProColumns[] => [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '名称',
        dataIndex: 'name',
        ellipsis: true,
    },
    {
        title: '命名空间',
        dataIndex: 'namespace',
        ellipsis: true,
    },
    {
        title: '创建时间',
        dataIndex: 'age',
        ellipsis: true,
    },
    {
        title: 'Ingress 类',
        dataIndex: 'ingressClass',
        ellipsis: true,
    },
    {
        title: '主机名',
        dataIndex: 'hosts',
        ellipsis: true,
        render: (hosts) => {
            return Array.isArray(hosts) ? hosts.join(', ') : hosts;
        },
    },
    {
        title: '负载均衡地址',
        dataIndex: 'loadBalancerAddress',
        ellipsis: true,
    },
    {
        title: '状态',
        dataIndex: 'status',
        ellipsis: true,
        render: (text) => <Tag color="green">{text}</Tag>,
    },
    // {
    //     title: '操作',
    //     valueType: 'option',
    //     key: 'option',
    //     width: 120,
    //     render: invokeGenOptionCol([
    //         {
    //             title: '查看',
    //             onClick: async (text, record, _, action) => {
    //                 console.log('查看Ingress:', record);
    //             }
    //         },
    //     ])
    // },
];

// CONFIGMAP 类型列定义
const getConfigMapColumns = (): ProColumns[] => [
    {
        dataIndex: 'index',
        title: '序号',
        valueType: 'indexBorder',
        width: 48,
    },
    {
        title: '名称',
        dataIndex: 'name',
        ellipsis: true,
    },
    {
        title: '命名空间',
        dataIndex: 'namespace',
        ellipsis: true,
    },
    {
        title: '创建时间',
        dataIndex: 'age',
        ellipsis: true,
    },
    // {
    //     title: '操作',
    //     valueType: 'option',
    //     key: 'option',
    //     width: 120,
    //     render: invokeGenOptionCol([
    //         {
    //             title: '查看',
    //             onClick: async (text, record, _, action) => {
    //                 console.log('查看ConfigMap:', record);
    //             }
    //         },
    //     ])
    // },
];

// 根据资源类型获取列
const getColumnsByResourceType = (resourceType: string): ProColumns[] => {
    switch (resourceType) {
        case T_POD:
            return getPodColumns();
        case T_SERVICE:
            return getServiceColumns();
        case T_DEPLOYMENT:
            return getDeploymentColumns();
        case T_INGRESS:
            return getIngressColumns();
        case T_CONFIGMAP:
            return getConfigMapColumns();
        default:
            return [];
    }
};


const Index = (obj: K8sTableProps) => {
    const {
        resourceType
    } = obj
    const actionRef = useRef();
    const [columns, setColumns] = useState<ProColumns[]>([]);
    const [dataSource, setDataSource] = useState([]);

    const { clusterId, instanceId } = useParams()



    // 初始化获取数据
    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await axiosJsonPost(API.k8sInstanceListResource, {
                    instanceId,
                    resourceType
                });
                if (res && res.code === 200 && res.data) {
                    setDataSource(res.data);
                }
            } catch (error) {
                console.error('Failed to fetch K8s resources:', error);
            }
        };
        fetchData();
    }, [instanceId, resourceType]);

    useEffect(() => {
        const cols = getColumnsByResourceType(resourceType);
        setColumns(cols);
    }, [resourceType]);


    if (!columns.length) {
        return <Empty description="不支持的资源类型" />;
    }

    // const apiEndpoint = getApiByResourceType(resourceType);
    // if (!apiEndpoint) {
    //     return <Empty description="API 配置缺失" />;
    // }

    return (
        <CommonTable
            tableProps={{
                actionRef,
                // request: invokePackProtableRequest({
                //     api: apiEndpoint,
                //     params: {
                //         clusterId,
                //         resourceType: currentResourceType
                //     }
                // }),
                columns,
                search: false,
                toolBarRender: false,
                dataSource
            }}
        />
    );
};

export default memo(Index);
