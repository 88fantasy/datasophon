import { ProTable } from '@ant-design/pro-components';
import { Tag } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import {
  T_CONFIGMAP,
  T_DEPLOYMENT,
  T_INGRESS,
  T_POD,
  T_SERVICE,
} from '@/constants/resourceType';
import { listK8sResources } from '@/services/datasophon/service';

interface K8sResourceProps {
  clusterId: number;
  instanceId: number;
  resourceType: string;
}

// ── 列定义（移植自旧版 datasophon-ui） ────────────────────────────────

const podColumns = [
  { dataIndex: 'index', title: '序号', valueType: 'indexBorder' as const, width: 48 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  {
    title: '状态',
    dataIndex: 'status',
    ellipsis: true,
    render: (text: string) => {
      const colors: Record<string, string> = {
        Running: 'green',
        Pending: 'orange',
        Failed: 'red',
        Succeeded: 'blue',
        Unknown: 'default',
      };
      return <Tag color={colors[text] ?? 'default'}>{text}</Tag>;
    },
  },
  { title: '创建时间', dataIndex: 'age', ellipsis: true },
  { title: '就绪', dataIndex: 'ready', ellipsis: true },
  { title: '重启次数', dataIndex: 'restartCount', ellipsis: true },
  { title: '节点名称', dataIndex: 'nodeName', ellipsis: true },
  { title: 'Pod IP', dataIndex: 'podIP', ellipsis: true },
];

const serviceColumns = [
  { dataIndex: 'index', title: '序号', valueType: 'indexBorder' as const, width: 48 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '命名空间', dataIndex: 'namespace', ellipsis: true },
  { title: '创建时间', dataIndex: 'age', ellipsis: true },
  {
    title: '服务类型',
    dataIndex: 'type',
    ellipsis: true,
    render: (text: string) => <Tag color="blue">{text}</Tag>,
  },
  { title: '集群 IP', dataIndex: 'clusterIP', ellipsis: true },
  { title: '外部 IP', dataIndex: 'externalIP', ellipsis: true },
  { title: '选择器标签', dataIndex: 'selector', ellipsis: true },
  {
    title: '状态',
    dataIndex: 'status',
    ellipsis: true,
    render: (text: string) => <Tag color="green">{text}</Tag>,
  },
];

const deploymentColumns = [
  { dataIndex: 'index', title: '序号', valueType: 'indexBorder' as const, width: 48 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '命名空间', dataIndex: 'namespace', ellipsis: true },
  { title: '创建时间', dataIndex: 'age', ellipsis: true },
  { title: '就绪副本数', dataIndex: 'readyReplicas', ellipsis: true },
  { title: '期望副本数', dataIndex: 'replicas', ellipsis: true },
  { title: '可用副本数', dataIndex: 'availableReplicas', ellipsis: true },
  {
    title: '状态',
    dataIndex: 'status',
    ellipsis: true,
    render: (text: string) => {
      const colors: Record<string, string> = {
        Ready: 'green',
        Progressing: 'orange',
        Failed: 'red',
      };
      return <Tag color={colors[text] ?? 'default'}>{text}</Tag>;
    },
  },
  {
    title: '镜像',
    dataIndex: 'images',
    ellipsis: true,
    render: (images: unknown) =>
      Array.isArray(images) ? images.join(', ') : (images as string),
  },
];

const ingressColumns = [
  { dataIndex: 'index', title: '序号', valueType: 'indexBorder' as const, width: 48 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '命名空间', dataIndex: 'namespace', ellipsis: true },
  { title: '创建时间', dataIndex: 'age', ellipsis: true },
  { title: 'Ingress 类', dataIndex: 'ingressClass', ellipsis: true },
  {
    title: '主机名',
    dataIndex: 'hosts',
    ellipsis: true,
    render: (hosts: unknown) =>
      Array.isArray(hosts) ? hosts.join(', ') : (hosts as string),
  },
  { title: '负载均衡地址', dataIndex: 'loadBalancerAddress', ellipsis: true },
  {
    title: '状态',
    dataIndex: 'status',
    ellipsis: true,
    render: (text: string) => <Tag color="green">{text}</Tag>,
  },
];

const configMapColumns = [
  { dataIndex: 'index', title: '序号', valueType: 'indexBorder' as const, width: 48 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '命名空间', dataIndex: 'namespace', ellipsis: true },
  { title: '创建时间', dataIndex: 'age', ellipsis: true },
];

function getColumnsByType(type: string) {
  switch (type) {
    case T_POD:
      return podColumns;
    case T_SERVICE:
      return serviceColumns;
    case T_DEPLOYMENT:
      return deploymentColumns;
    case T_INGRESS:
      return ingressColumns;
    case T_CONFIGMAP:
      return configMapColumns;
    default:
      return [{ title: '名称', dataIndex: 'name', ellipsis: true }];
  }
}

/**
 * K8s 资源列表（Pod / Service / Deployment / Ingress / ConfigMap）。
 * 每次 resourceType 变化时重新加载。
 */
const K8sResource: React.FC<K8sResourceProps> = ({ clusterId, instanceId, resourceType }) => {
  const [dataSource, setDataSource] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);
  const cancelRef = useRef(false);

  useEffect(() => {
    cancelRef.current = false;
    setLoading(true);
    listK8sResources(clusterId, instanceId, resourceType)
      .then((res) => {
        if (cancelRef.current) return;
        const data = Array.isArray(res) ? res : ((res as any).data ?? []);
        setDataSource(data);
      })
      .catch(() => {
        /* global error handler */
      })
      .finally(() => {
        if (!cancelRef.current) setLoading(false);
      });
    return () => {
      cancelRef.current = true;
    };
  }, [clusterId, instanceId, resourceType]);

  return (
    <ProTable
      rowKey="name"
      search={false}
      options={false}
      pagination={false}
      loading={loading}
      dataSource={dataSource}
      columns={getColumnsByType(resourceType) as any}
    />
  );
};

export default K8sResource;
