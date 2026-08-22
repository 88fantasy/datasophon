import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { history, useIntl } from '@umijs/max';
import { Card, Tag } from 'antd';
import { useContext, useRef, useState } from 'react';
import ClusterContext from '@/context/ClusterContext';
import FreshnessAlert from './FreshnessAlert';
import LineageOverview, { LAYER_COLOR } from './LineageOverview';
import type { NodeMeta, SnapshotFreshness, SourceFreshness } from './service';
import { listTables } from './service';

const DW_LAYERS = ['CDC', 'ODS', 'DWD', 'DIM', 'DWS', 'ADS', 'TMP'];

const LineageTableList: React.FC = () => {
  const clusterCtx = useContext(ClusterContext);
  if (!clusterCtx) {
    throw new Error(
      'ClusterContext not found — Lineage list must be rendered inside ClusterLayout',
    );
  }
  const { clusterId } = clusterCtx;
  const intl = useIntl();
  const t = (id: string, defaultMessage: string) =>
    intl.formatMessage({ id, defaultMessage });

  const actionRef = useRef<ActionType>(null);
  const [freshness, setFreshness] = useState<{
    snapshot: SnapshotFreshness;
    sourceFreshness: SourceFreshness;
  }>();
  const [overviewRefreshKey, setOverviewRefreshKey] = useState(0);

  const handleRebuilt = () => {
    setOverviewRefreshKey((value) => value + 1);
    actionRef.current?.reload();
  };

  const columns: ProColumns<NodeMeta>[] = [
    {
      title: t('pages.lineage.column.canonicalName', '表全限定名'),
      dataIndex: 'keyword',
      key: 'canonicalName',
      ellipsis: true,
      render: (_, record) => (
        <a
          onClick={() =>
            history.push(`/cluster/${clusterId}/lineage/${record.id}`)
          }
        >
          {record.canonicalName}
        </a>
      ),
    },
    {
      title: t('pages.lineage.column.dwLayer', '分层'),
      dataIndex: 'layer',
      key: 'dwLayer',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        DW_LAYERS.map((layer) => [layer, { text: layer }]),
      ),
      render: (_, record) =>
        record.dwLayer ? (
          <Tag color={LAYER_COLOR[record.dwLayer] ?? 'default'}>
            {record.dwLayer}
          </Tag>
        ) : (
          <Tag>UNKNOWN</Tag>
        ),
    },
    {
      title: t('pages.lineage.column.connector', 'Connector'),
      dataIndex: 'connector',
      width: 120,
    },
    {
      title: t('pages.lineage.column.catalogName', 'Catalog'),
      dataIndex: 'catalogName',
      search: false,
      width: 140,
      ellipsis: true,
    },
    {
      title: t('pages.lineage.column.database', '库名'),
      dataIndex: 'database',
      key: 'databaseName',
      width: 140,
      ellipsis: true,
      render: (_, record) => record.databaseName,
    },
    {
      title: t('pages.lineage.column.tableName', '表名'),
      dataIndex: 'tableName',
      search: false,
      width: 160,
      ellipsis: true,
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      {freshness && (
        <FreshnessAlert
          clusterId={clusterId}
          snapshot={freshness.snapshot}
          sourceFreshness={freshness.sourceFreshness}
          onRebuilt={handleRebuilt}
        />
      )}
      <Card
        size="small"
        title={t('pages.lineage.overview.title', '分层概览')}
        style={{ marginBottom: 16 }}
      >
        <LineageOverview
          clusterId={clusterId}
          refreshKey={overviewRefreshKey}
        />
      </Card>
      <ProTable<NodeMeta>
        actionRef={actionRef}
        rowKey="id"
        search={{ filterType: 'light' }}
        params={{ clusterId }}
        request={async (params) => {
          const { current, pageSize, keyword, layer, connector, database } =
            params;
          const result = await listTables({
            clusterId,
            page: current ?? 1,
            size: pageSize ?? 20,
            keyword,
            layer,
            connector,
            database,
          });
          setFreshness({
            snapshot: result.snapshot,
            sourceFreshness: result.sourceFreshness,
          });
          return {
            data: result.data.list,
            total: result.data.total,
            success: true,
          };
        }}
        columns={columns}
        locale={{
          emptyText: t('pages.lineage.table.empty', '暂无血缘表数据'),
        }}
      />
    </div>
  );
};

export default LineageTableList;
