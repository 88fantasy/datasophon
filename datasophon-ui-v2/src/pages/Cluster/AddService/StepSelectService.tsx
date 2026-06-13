import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';
import { listNewestServices } from '@/services/addService';

interface Props {
  clusterId: number;
  manifest: DATASOPHON.ManifestContext | null;
  active: boolean;
  /** 已勾选的服务（容器受控） */
  value: DATASOPHON.FrameService[];
  onChange: (rows: DATASOPHON.FrameService[]) => void;
}

const columns: ColumnsType<DATASOPHON.FrameService> = [
  {
    title: '服务',
    dataIndex: 'label',
    ellipsis: true,
    render: (text, row) => text || row.serviceName,
  },
  { title: '描述', dataIndex: 'serviceDesc', ellipsis: true },
  { title: '版本', dataIndex: 'serviceVersion', width: 140, ellipsis: true },
  {
    title: '状态',
    dataIndex: 'installed',
    width: 90,
    render: (installed: boolean) =>
      installed ? <Tag color="green">已安装</Tag> : <Tag>未安装</Tag>,
  },
];

/** 步骤 2：按部署清单选择待安装服务（清单中出现的服务默认勾选）。 */
const StepSelectService: React.FC<Props> = ({
  clusterId,
  manifest,
  active,
  value,
  onChange,
}) => {
  const [dataSource, setDataSource] = useState<DATASOPHON.FrameService[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!active || !manifest) return;
    let cancelled = false;
    setLoading(true);
    listNewestServices(clusterId, manifest)
      .then((res) => {
        if (cancelled) return;
        const list = res.data ?? [];
        setDataSource(list);
        // 清单勾选的服务默认选中（仅首次加载，避免覆盖用户手动调整）
        const preset = list.filter((s) => s.selected);
        if (preset.length > 0) {
          onChange(preset);
        }
      })
      .catch(() => {
        /* 全局错误处理已弹消息 */
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // onChange 由容器 useState setter 提供，引用稳定
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, clusterId, manifest]);

  return (
    <Table<DATASOPHON.FrameService>
      rowKey="id"
      size="small"
      loading={loading}
      dataSource={dataSource}
      columns={columns}
      pagination={false}
      scroll={{ y: '40vh' }}
      style={{ marginBottom: 20 }}
      rowSelection={{
        selectedRowKeys: value.map((s) => s.id),
        onChange: (_, rows) => onChange(rows),
      }}
    />
  );
};

export default StepSelectService;
