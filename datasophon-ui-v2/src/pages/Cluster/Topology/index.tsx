import {
  AimOutlined,
  ApartmentOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CloseOutlined,
  CloudServerOutlined,
  DeploymentUnitOutlined,
  ExpandOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  MoonOutlined,
  ReloadOutlined,
  SearchOutlined,
  SunOutlined,
} from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import {
  Alert,
  theme as antdTheme,
  Button,
  ConfigProvider,
  Input,
  Segmented,
  Spin,
  Tooltip,
} from 'antd';
import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { listClusters } from '@/services/cluster';
import { serviceIconFor } from '../ObservabilityCollector/serviceIcon';
import styles from './index.module.css';
import TopologyPlan from './TopologyPlan';
import { loadTopology } from './topologyData';
import { visibleTopologyNodes } from './topologyHierarchy';
import {
  type ResourceState,
  TOPOLOGY_ZONES,
  type TopologyMode,
  type TopologyNode,
  type TopologySnapshot,
  type TopologyTheme,
  type ZoneId,
} from './types';

const TopologyScene = lazy(() => import('./TopologyScene'));
const REFRESH_INTERVAL = 5 * 60 * 1000;
const STATES: Record<ResourceState, { label: string; color: string }> = {
  running: { label: '运行', color: '#21aa84' },
  warning: { label: '告警', color: '#d79b35' },
  stopped: { label: '停止', color: '#dc6575' },
  unknown: { label: '未知', color: '#8e9aab' },
};

function ResourceIcon({ node }: { node: TopologyNode }) {
  if (node.serviceName)
    return (
      <img
        src={serviceIconFor(node.serviceName).src}
        alt=""
        width={22}
        height={22}
      />
    );
  return node.kind === 'browser' ? <GlobalOutlined /> : <CloudServerOutlined />;
}

export default function ClusterTopology() {
  const { clusterId: routeId } = useParams<{ clusterId: string }>();
  const clusterId = Number(routeId);
  const validId =
    /^\d+$/.test(routeId ?? '') &&
    Number.isSafeInteger(clusterId) &&
    clusterId > 0;
  const [cluster, setCluster] = useState<DATASOPHON.ClusterResponse>();
  const [snapshot, setSnapshot] = useState<TopologySnapshot>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [renderError, setRenderError] = useState<string>();
  const [refreshKey, setRefreshKey] = useState(0);
  const [resetKey, setResetKey] = useState(0);
  const [mode, setMode] = useState<TopologyMode>('3d');
  const [theme, setTheme] = useState<TopologyTheme>('light');
  const [focusZone, setFocusZone] = useState<ZoneId | null>(null);
  const [hostId, setHostId] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const pageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setHostId(null);
    setSnapshot(undefined);
    setCluster(undefined);
    setSelectedId(null);
    setFocusZone(null);
    setSearch('');
  }, [clusterId]);

  useEffect(() => {
    let cancelled = false;
    if (!validId) {
      setError('集群 ID 无效，请从集群列表重新进入。');
      setLoading(false);
      return;
    }
    setLoading(true);
    const refresh = async () => {
      try {
        const response = await listClusters();
        const current = response.data.find((item) => item.id === clusterId);
        if (!current)
          throw new Error(
            '未找到该集群，请检查集群是否已删除或当前账号是否有访问权限。',
          );
        const data = await loadTopology(current);
        if (cancelled) return;
        setHostId((id) =>
          data.nodes.some((node) => node.id === id && node.kind === 'host')
            ? id
            : null,
        );
        setCluster(current);
        setSnapshot(data);
        setError(undefined);
        setSelectedId((id) =>
          data.nodes.some((node) => node.id === id) ? id : null,
        );
      } catch (reason) {
        if (!cancelled)
          setError(
            reason instanceof Error
              ? reason.message
              : '读取集群资源失败，请稍后重试。',
          );
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void refresh();
    return () => {
      cancelled = true;
    };
  }, [clusterId, validId, refreshKey]);

  useEffect(() => {
    const timer = window.setInterval(
      () => setRefreshKey((key) => key + 1),
      REFRESH_INTERVAL,
    );
    return () => window.clearInterval(timer);
  }, [clusterId]);

  const nodes = snapshot?.nodes ?? [];
  const visibleNodes = useMemo(
    () => visibleTopologyNodes(snapshot?.nodes ?? [], focusZone, hostId),
    [snapshot, focusZone, hostId],
  );
  const currentHost = nodes.find((node) => node.id === hostId);
  useEffect(() => {
    if (currentHost) setFocusZone(currentHost.zone);
    else if (
      snapshot &&
      focusZone &&
      !snapshot.nodes.some((node) => node.zone === focusZone && !node.parentId)
    ) {
      setFocusZone(null);
      setHostId(null);
    }
  }, [currentHost, snapshot, focusZone]);
  const selected = visibleNodes.find((node) => node.id === selectedId);
  const focused = TOPOLOGY_ZONES.find((zone) => zone.id === focusZone);
  const inventory = nodes.filter((node) => node.source === 'inventory');
  const hostCount = new Set(
    inventory
      .filter((node) => node.kind === 'host')
      .map((node) => node.resourceId ?? node.id),
  ).size;
  const services = inventory.filter((node) => node.kind === 'service');
  const alerts = new Set(
    inventory
      .filter((node) => node.state === 'warning')
      .map((node) => node.resourceId ?? node.id),
  ).size;
  const needle = search.trim().toLocaleLowerCase();
  const highlightedIds = useMemo(() => {
    if (!needle) return null;
    const matches = (node: TopologyNode) =>
      [
        node.label,
        node.subtitle,
        node.address,
        node.serviceName,
        ...node.instances.map(
          (item) => `${item.label} ${item.hostname ?? ''} ${item.ports ?? ''}`,
        ),
      ].some((value) => value?.toLocaleLowerCase().includes(needle));
    const matched = (snapshot?.nodes ?? []).filter(matches);
    return visibleNodes
      .filter(
        (node) =>
          matches(node) ||
          matched.some((child) =>
            node.kind === 'cluster'
              ? child.zone === node.zone
              : child.parentId === node.id,
          ),
      )
      .map((node) => node.id);
  }, [snapshot, visibleNodes, needle]);
  const listedNodes = visibleNodes.filter(
    (node) =>
      (!focusZone || node.zone === focusZone) &&
      (!highlightedIds || highlightedIds.includes(node.id)),
  );

  const enterZone = useCallback((zone: ZoneId) => {
    setHostId(null);
    setSearch('');
    setFocusZone(zone);
    setSelectedId(null);
  }, []);
  const handleRenderError = useCallback((message: string) => {
    setRenderError(message);
    setMode('2d');
  }, []);
  const selectResource = (id: string | null) => {
    const node = visibleNodes.find((node) => node.id === id);
    if (node?.kind === 'cluster') enterZone(node.zone);
    else if (node?.kind === 'host' && node.zone !== 'k8s') {
      setHostId(node.id);
      setSelectedId(null);
      setSearch('');
    } else setSelectedId(id);
  };
  const sceneProps = {
    nodes: visibleNodes,
    scopeKey: hostId || focusZone || 'overview',
    edges: snapshot?.edges ?? [],
    mode,
    theme,
    focusZone,
    selectedId,
    highlightedIds,
    resetKey,
    onSelect: selectResource,
    onEnterZone: enterZone,
    onError: handleRenderError,
  };

  return (
    <ConfigProvider
      theme={{
        inherit: false,
        algorithm:
          theme === 'dark'
            ? antdTheme.darkAlgorithm
            : antdTheme.defaultAlgorithm,
        token: { colorPrimary: '#477fea', borderRadius: 8 },
      }}
    >
      <div
        ref={pageRef}
        className={styles.page}
        data-theme={theme}
        data-testid="cluster-topology"
      >
        <header className={styles.header}>
          <div className={styles.heading}>
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              aria-label="返回集群列表"
              onClick={() => history.push('/colony')}
            />
            <span className={styles.brand}>
              <DeploymentUnitOutlined />
            </span>
            <div>
              <h1>集群部署拓扑</h1>
              <span className={styles.subtitle}>
                {cluster?.clusterName ?? '基础设施全景'}
                <span className={styles.separator}>/</span>INFRASTRUCTURE ATLAS
              </span>
            </div>
          </div>
          <div className={styles.headerActions}>
            <span className={styles.refreshInfo}>
              <i className={loading ? styles.updating : ''} />
              {loading ? '正在同步' : error ? '同步失败' : '每 5 分钟同步'}
            </span>
            <Tooltip title="刷新资源">
              <Button
                icon={<ReloadOutlined spin={loading} />}
                aria-label="刷新资源"
                disabled={loading || !validId}
                onClick={() => setRefreshKey((key) => key + 1)}
              />
            </Tooltip>
            <Tooltip
              title={theme === 'light' ? '切换深色主题' : '切换浅色主题'}
            >
              <Button
                icon={theme === 'light' ? <MoonOutlined /> : <SunOutlined />}
                aria-label={theme === 'light' ? '切换深色主题' : '切换浅色主题'}
                onClick={() =>
                  setTheme((value) => (value === 'light' ? 'dark' : 'light'))
                }
              />
            </Tooltip>
            <Tooltip title="全屏浏览">
              <Button
                icon={<ExpandOutlined />}
                aria-label="全屏浏览"
                onClick={() => {
                  if (document.fullscreenElement)
                    void document.exitFullscreen();
                  else
                    void pageRef.current
                      ?.requestFullscreen?.()
                      .catch(() =>
                        setRenderError(
                          '浏览器未允许全屏，仍可在当前页面浏览。',
                        ),
                      );
                }}
              />
            </Tooltip>
          </div>
        </header>

        <div className={styles.workspace}>
          <aside className={styles.sidebar} aria-label="分区和资源导航">
            <div className={styles.overviewLabel}>
              资源总览 <span>当前集群</span>
            </div>
            <div className={styles.stats}>
              <div>
                <strong>{snapshot ? hostCount : '—'}</strong>
                <span>服务器</span>
              </div>
              <div>
                <strong>{snapshot ? services.length : '—'}</strong>
                <span>服务</span>
              </div>
              <div>
                <strong className={alerts ? styles.warningText : ''}>
                  {snapshot ? alerts : '—'}
                </strong>
                <span>告警资源</span>
              </div>
            </div>
            <div className={styles.sectionLabel}>
              子集群 <span>点击进入</span>
            </div>
            <nav className={styles.zones} aria-label="部署分区">
              <button
                type="button"
                className={!focusZone ? styles.activeZone : ''}
                onClick={() => {
                  setHostId(null);
                  setSearch('');
                  setFocusZone(null);
                  setSelectedId(null);
                }}
              >
                <ApartmentOutlined />
                <span>集群全景</span>
                <span className={styles.zoneCount}>{inventory.length}</span>
              </button>
              {TOPOLOGY_ZONES.filter((zone) =>
                nodes.some((node) => node.zone === zone.id && !node.parentId),
              ).map((zone) => (
                <button
                  type="button"
                  key={zone.id}
                  aria-label={`进入${zone.label}分区`}
                  className={focusZone === zone.id ? styles.activeZone : ''}
                  onClick={() => enterZone(zone.id)}
                >
                  <i style={{ background: zone.color }} />
                  <span>{zone.label}</span>
                  <span className={styles.zoneCount}>
                    {
                      nodes.filter(
                        (node) =>
                          node.zone === zone.id &&
                          !node.parentId &&
                          (zone.id !== 'k8s' || node.kind === 'host'),
                      ).length
                    }
                  </span>
                </button>
              ))}
            </nav>
            <div className={styles.sectionLabel}>
              资源索引 <span>{listedNodes.length}</span>
            </div>
            <Input
              prefix={<SearchOutlined />}
              placeholder="搜索服务、主机或 IP"
              aria-label="搜索服务、主机或 IP"
              allowClear
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <section className={styles.resourceList} aria-label="资源列表">
              {listedNodes.map((node) => (
                <button
                  type="button"
                  key={node.id}
                  aria-label={`查看${node.label}`}
                  className={
                    selectedId === node.id ? styles.activeResource : ''
                  }
                  onClick={() => selectResource(node.id)}
                >
                  <span className={styles.resourceIcon}>
                    <ResourceIcon node={node} />
                  </span>
                  <span className={styles.resourceName}>
                    <b>{node.label}</b>
                    <small>{node.subtitle}</small>
                  </span>
                  <i
                    className={styles.stateDot}
                    style={{ background: STATES[node.state].color }}
                    title={STATES[node.state].label}
                  />
                </button>
              ))}
              {!listedNodes.length && (
                <div className={styles.emptyList}>
                  {needle ? '没有匹配的资源' : '此分区暂无登记资源'}
                </div>
              )}
            </section>
            <div className={styles.sidebarFoot}>
              <InfoCircleOutlined />
              <span>
                {cluster?.archType === 'k8s' ? (
                  <>
                    全景显示节点，进入 Kubernetes 查看 Service / CR
                    <br />
                    不展示 Pod，不推断服务所在节点
                  </>
                ) : (
                  <>
                    仅显示当前集群已登记资源
                    <br />
                    按节点标签归类，未配置归入“其他”
                  </>
                )}
              </span>
            </div>
          </aside>

          <main className={styles.main}>
            <div className={styles.toolbar}>
              <div className={styles.breadcrumb}>
                <button
                  type="button"
                  onClick={() => {
                    setHostId(null);
                    setSearch('');
                    setFocusZone(null);
                    setSelectedId(null);
                  }}
                >
                  集群全景
                </button>
                {focused && (
                  <>
                    <span>/</span>
                    <button type="button" onClick={() => enterZone(focused.id)}>
                      {focused.label}
                    </button>
                    {currentHost && (
                      <>
                        <span>/</span>
                        <strong>{currentHost.label}</strong>
                      </>
                    )}
                  </>
                )}
                <span className={styles.viewTag}>部署视图</span>
              </div>
              <div className={styles.viewControls}>
                <Segmented
                  aria-label="拓扑视图模式"
                  value={mode}
                  options={[
                    { label: '3D 立体', value: '3d' },
                    { label: '2.5D 俯视', value: '2.5d' },
                    { label: '2D 平面', value: '2d' },
                  ]}
                  onChange={(value) => {
                    setMode(value as TopologyMode);
                    setRenderError(undefined);
                  }}
                />
                <Tooltip title="重置视角">
                  <Button
                    type="text"
                    icon={<AimOutlined />}
                    aria-label="重置视角"
                    onClick={() => setResetKey((key) => key + 1)}
                  />
                </Tooltip>
              </div>
            </div>
            {(error || renderError || !!snapshot?.warnings.length) && (
              <div className={styles.notices} aria-live="polite">
                {error && (
                  <Alert
                    type="error"
                    showIcon
                    title={
                      snapshot
                        ? '刷新失败，当前保留上次成功快照'
                        : '资源加载失败'
                    }
                    description={error}
                  />
                )}
                {renderError && (
                  <Alert type="warning" showIcon title={renderError} />
                )}
                {!!snapshot?.warnings.length && (
                  <details className={styles.warnings}>
                    <summary>
                      部分资源信息不完整（{snapshot.warnings.length} 项）
                    </summary>
                    <ul>
                      {snapshot.warnings.map((warning) => (
                        <li key={warning}>{warning}</li>
                      ))}
                    </ul>
                  </details>
                )}
              </div>
            )}
            <div className={styles.stage}>
              {snapshot ? (
                <Suspense
                  fallback={
                    <div className={styles.loading}>
                      <Spin />
                      <span>正在构建 3D 场景</span>
                    </div>
                  }
                >
                  <div
                    style={{
                      width: '100%',
                      height: '100%',
                      display: mode === '2d' ? 'none' : 'block',
                    }}
                    aria-hidden={mode === '2d'}
                  >
                    <TopologyScene {...sceneProps} />
                  </div>
                  <div
                    style={{
                      width: '100%',
                      height: '100%',
                      display: mode === '2d' ? 'block' : 'none',
                    }}
                    aria-hidden={mode !== '2d'}
                  >
                    <TopologyPlan {...sceneProps} />
                  </div>
                </Suspense>
              ) : (
                <div className={styles.loading}>
                  {loading ? (
                    <>
                      <Spin size="large" />
                      <span>正在读取主机和服务部署信息</span>
                    </>
                  ) : (
                    <>
                      <CloudServerOutlined />
                      <span>暂无可展示的部署快照</span>
                      <Button
                        onClick={() => setRefreshKey((key) => key + 1)}
                        disabled={!validId}
                      >
                        重新加载
                      </Button>
                    </>
                  )}
                </div>
              )}
              <div className={styles.stageCaption}>
                <span>
                  {currentHost
                    ? `${currentHost.label} · 服务器内部服务`
                    : focused
                      ? focused.subtitle
                      : cluster?.archType === 'k8s'
                        ? 'Kubernetes 节点全景'
                        : '子集群 → 服务器 → 服务'}
                </span>
                <small>
                  {mode === '3d'
                    ? '拖拽旋转 · 滚轮缩放 · 右键平移'
                    : '拖拽平移 · 滚轮缩放'}
                  <br />
                  {cluster?.archType === 'k8s'
                    ? '点击 Kubernetes 集群查看 Service / CR · 点击节点或服务查看详情'
                    : '点击子集群或服务器下钻 · 点击服务查看详情'}
                </small>
              </div>
              {snapshot && focusZone && !visibleNodes.length && (
                <div className={styles.emptyNotice}>
                  {hostId
                    ? '此服务器暂无已登记服务'
                    : focusZone === 'k8s'
                      ? '暂无可展示的 Service / CR，请检查服务接管登记及资源读取结果。'
                      : '此子集群暂无可展示资源，请检查登记信息与类别标签。'}
                </div>
              )}
            </div>
            <footer className={styles.footer}>
              <div className={styles.legend}>
                <span>
                  <i className={styles.solidLine} />
                  部署归属
                </span>

                <span className={styles.communications}>通信数据待接入</span>
              </div>
              <span className={styles.snapshotTime}>
                快照：
                {snapshot
                  ? new Date(snapshot.observedAt).toLocaleTimeString('zh-CN', {
                      hour12: false,
                    })
                  : '—'}
              </span>
            </footer>
          </main>

          {selected && (
            <aside className={styles.inspector} aria-label="资源详情">
              <div className={styles.inspectorTop}>
                <span>资源详情</span>
                <Button
                  type="text"
                  icon={<CloseOutlined />}
                  aria-label="关闭资源详情"
                  onClick={() => setSelectedId(null)}
                />
              </div>
              <div className={styles.inspectorHero}>
                <span>
                  <ResourceIcon node={selected} />
                </span>
                <h2>{selected.label}</h2>
                <p>{selected.subtitle}</p>
              </div>
              <div className={styles.inspectorTags}>
                <span style={{ color: STATES[selected.state].color }}>
                  <i
                    className={styles.stateDot}
                    style={{ background: STATES[selected.state].color }}
                  />
                  {selected.source === 'schematic'
                    ? '架构示意'
                    : STATES[selected.state].label}
                </span>
                <span>
                  {
                    TOPOLOGY_ZONES.find((zone) => zone.id === selected.zone)
                      ?.label
                  }
                </span>
              </div>
              {selected.source === 'schematic' && (
                <p className={styles.schematicNote}>
                  该节点用于说明外部访问与前置接入结构，尚未绑定实际设备。
                </p>
              )}
              <dl className={styles.details}>
                {selected.details.map((detail) => (
                  <div key={detail.label}>
                    <dt>{detail.label}</dt>
                    <dd>{detail.value}</dd>
                  </div>
                ))}
              </dl>
              {!!selected.instances.length && (
                <>
                  <div className={styles.sectionLabel}>
                    部署实例 <span>{selected.instances.length}</span>
                  </div>
                  <div className={styles.instances}>
                    {selected.instances.map((instance) => (
                      <div key={instance.id}>
                        <b>
                          <i
                            className={styles.stateDot}
                            style={{ background: STATES[instance.state].color }}
                          />
                          {instance.label}
                        </b>
                        <span>{instance.hostname ?? '部署主机未确认'}</span>
                        {instance.ports && <small>{instance.ports}</small>}
                      </div>
                    ))}
                  </div>
                </>
              )}
              <div className={styles.inspectorActions}>
                <Button block onClick={() => enterZone(selected.zone)}>
                  返回所属子集群 <ArrowRightOutlined />
                </Button>
                {selected.href && (
                  <Button
                    type="primary"
                    block
                    onClick={() => selected.href && history.push(selected.href)}
                  >
                    打开资源管理
                  </Button>
                )}
              </div>
            </aside>
          )}
        </div>
      </div>
    </ConfigProvider>
  );
}
