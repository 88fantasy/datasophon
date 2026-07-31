import { useIntl } from '@umijs/max';
import { Empty, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { getOverview } from './service';
import type { OverviewData } from './service';

interface LineageOverviewProps {
  clusterId: number;
  /** 变化时重新拉取，用于列表页在重建后刷新概览。 */
  refreshKey?: number;
}

export const LAYER_COLOR: Record<string, string> = {
  CDC: 'default',
  ODS: 'blue',
  DWD: 'geekblue',
  DIM: 'purple',
  DWS: 'cyan',
  ADS: 'green',
  TMP: 'orange',
  UNKNOWN: 'default',
};

const LineageOverview: React.FC<LineageOverviewProps> = ({
  clusterId,
  refreshKey,
}) => {
  const intl = useIntl();
  const t = (id: string, defaultMessage: string) =>
    intl.formatMessage({ id, defaultMessage });
  const [overview, setOverview] = useState<OverviewData>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!clusterId) return;
    setLoading(true);
    getOverview(clusterId)
      .then((res) => setOverview(res.data))
      .finally(() => setLoading(false));
  }, [clusterId, refreshKey]);

  const layers = (overview?.layers ?? []).filter((layer) => layer.nodeCount > 0);
  const maxCount = Math.max(1, ...layers.map((layer) => layer.nodeCount));
  const edges = (overview?.edges ?? []).filter((edge) => edge.count > 0);

  return (
    <Spin spinning={loading}>
      {layers.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t(
            'pages.lineage.overview.empty',
            '暂无分层数据',
          )}
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {layers.map((layer) => (
              <div
                key={layer.layer}
                style={{ display: 'flex', alignItems: 'center', gap: 8 }}
              >
                <Tag
                  color={LAYER_COLOR[layer.layer] ?? 'default'}
                  style={{ width: 56, textAlign: 'center', margin: 0 }}
                >
                  {layer.layer}
                </Tag>
                <div
                  style={{
                    flex: 1,
                    background: 'rgba(0,0,0,0.04)',
                    borderRadius: 4,
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      width: `${(layer.nodeCount / maxCount) * 100}%`,
                      minWidth: 4,
                      height: 20,
                      background: '#597ef7',
                      borderRadius: 4,
                    }}
                  />
                </div>
                <span style={{ width: 40, textAlign: 'right' }}>
                  {layer.nodeCount}
                </span>
              </div>
            ))}
          </div>
          {edges.length > 0 && (
            <div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {t('pages.lineage.overview.edgesTitle', '层间血缘关系')}
              </Typography.Text>
              <div style={{ marginTop: 4 }}>
                <Space size={[8, 8]} wrap>
                  {edges.map((edge) => (
                    <Tag key={`${edge.srcLayer}-${edge.dstLayer}`}>
                      {edge.srcLayer} → {edge.dstLayer} ({edge.count})
                    </Tag>
                  ))}
                </Space>
              </div>
            </div>
          )}
        </div>
      )}
    </Spin>
  );
};

export default LineageOverview;
