import { useIntl } from '@umijs/max';
import { Alert, Button, message, Space } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { rebuild } from './service';
import type { SnapshotFreshness, SourceFreshness } from './service';

/** ageSeconds 由后端算好（服务器时钟为准），比前端再用 dayjs 相对当前时刻二次计算更可靠。 */
function formatAge(ageSeconds: number) {
  if (ageSeconds < 60) return `${ageSeconds}s`;
  if (ageSeconds < 3600) return `${Math.floor(ageSeconds / 60)}min`;
  return `${Math.floor(ageSeconds / 3600)}h`;
}

interface FreshnessAlertProps {
  clusterId: number;
  snapshot: SnapshotFreshness;
  sourceFreshness: SourceFreshness;
  onRebuilt?: () => void;
}

const FreshnessAlert: React.FC<FreshnessAlertProps> = ({
  clusterId,
  snapshot,
  sourceFreshness,
  onRebuilt,
}) => {
  const intl = useIntl();
  const t = (id: string, defaultMessage: string) =>
    intl.formatMessage({ id, defaultMessage });
  const [rebuilding, setRebuilding] = useState(false);

  const type = snapshot.lastRebuildError
    ? 'error'
    : snapshot.stale
      ? 'warning'
      : 'info';

  // builtAt/updateTime 等均为后端 java.time.Instant 序列化的 UTC ISO 字符串（带 Z 后缀），
  // dayjs 原生按 Date 解析已能正确换算本地时区，不需要 .utc().local() 二次转换。
  const builtAtText = `${dayjs(snapshot.builtAt).format('YYYY-MM-DD HH:mm:ss')}（${formatAge(snapshot.ageSeconds)} 前）`;
  const sourceStatusText: Record<string, string> = {
    OK: '',
    LAGGING: t('pages.lineage.freshness.sourceLagging', '采集侧血缘事件已滞后'),
    NO_DATA: t(
      'pages.lineage.freshness.sourceNoData',
      '采集侧尚未收到任何血缘事件',
    ),
    UNKNOWN: t('pages.lineage.freshness.sourceUnknown', '采集侧新鲜度未知'),
  };
  const sourceText =
    sourceFreshness.status && sourceFreshness.status !== 'OK'
      ? sourceStatusText[sourceFreshness.status]
      : '';

  const handleRebuild = async () => {
    setRebuilding(true);
    try {
      await rebuild(clusterId);
      message.success(
        t('pages.lineage.freshness.rebuildAccepted', '已提交重建请求'),
      );
      onRebuilt?.();
    } finally {
      setRebuilding(false);
    }
  };

  return (
    <Alert
      type={type}
      showIcon
      style={{ marginBottom: 12 }}
      title={
        <Space size={12} wrap>
          <span>
            {t('pages.lineage.freshness.snapshotBuiltAt', '快照构建于')}{' '}
            {builtAtText}（generation {snapshot.generation}
            {snapshot.generation !== snapshot.targetGeneration
              ? ` / ${snapshot.targetGeneration}`
              : ''}
            ）
          </span>
          {snapshot.stale && (
            <span>
              {t('pages.lineage.freshness.stale', '快照已过期，建议重建')}
            </span>
          )}
          {snapshot.lastRebuildError && (
            <span>
              {t('pages.lineage.freshness.lastError', '上次重建失败：')}
              {snapshot.lastRebuildError}
            </span>
          )}
          {sourceText && <span>{sourceText}</span>}
        </Space>
      }
      action={
        <Button size="small" loading={rebuilding} onClick={handleRebuild}>
          {t('pages.lineage.freshness.rebuildNow', '立即重建')}
        </Button>
      }
    />
  );
};

export default FreshnessAlert;
