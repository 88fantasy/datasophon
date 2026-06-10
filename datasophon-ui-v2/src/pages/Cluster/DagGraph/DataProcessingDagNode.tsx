/**
 * DAG 节点 React 组件（@antv/x6-react-shape）
 * v2 移植自 datasophon-ui/src/components/DagModal/DataProcessingDagNode.tsx
 *
 * 相对原版的变化：
 * 1. 移除流程图编辑器工具（NodeType / createNode / createEdge / + 号下拉等）
 * 2. 多源多 Tab 日志 Modal → 单文本内联 Modal（只保留主机执行日志）
 * 3. axios 封装 → @umijs/max request
 * 4. gobalEvent/uiEvent → dagEvent/dagUiEvent
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Edge, Graph, Path } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import insertCss from 'insert-css';
import { Card, Modal, Progress, Spin, Tooltip } from 'antd';
import { isEqual } from 'lodash-es';
import { request } from '@umijs/max';

import dagEvent, { dagUiEvent } from './dagEvent';
import { invokeGenStatusDom } from './dagStatus';

const T_K8S = 'k8s';

interface LogState {
  open: boolean;
  loading: boolean;
  text: string;
  title: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const Index = (props: any) => {
  const { node } = props;
  const elId = useRef(`dag-node-${Math.random().toString(36).slice(2)}`);

  const [nodeData, setNodeData] = useState<any>(() => node?.getData()?.data ?? {});
  const archType = useMemo(() => node?.getData()?.archType, [node]);

  const [logState, setLogState] = useState<LogState>({
    open: false,
    loading: false,
    text: '',
    title: '',
  });

  const { nodeName, k8s = [], roles = [], clusterId } = nodeData;

  /** 打开主机命令执行日志（物理集群节点专用） */
  const openCmdLog = async (
    e: React.MouseEvent,
    hostCommandId: string,
    clId: number,
    title: string,
  ) => {
    e.stopPropagation();
    if (!hostCommandId || !clId) return;
    setLogState({ open: true, loading: true, text: '', title });
    try {
      const res = await request<any>(
        '/ddh/api/cluster/service/command/host/command/getHostCommandLog',
        { method: 'GET', params: { clusterId: clId, hostCommandId } },
      );
      const text: string =
        typeof res === 'string'
          ? res
          : typeof res?.data === 'string'
            ? res.data
            : ((res?.data?.logText as string) ?? JSON.stringify(res, null, 2));
      setLogState((p) => ({ ...p, loading: false, text }));
    } catch {
      setLogState((p) => ({ ...p, loading: false, text: '获取日志失败' }));
    }
  };

  /** 鼠标进入节点主区域时显示连接桩 */
  const onMainMouseEnter = () => {
    const ports = node.getPorts() || [];
    ports.forEach((port: { id: string }) => {
      node.setPortProp(port.id, 'attrs/circle', { fill: '#fff', stroke: '#85A5FF' });
    });
  };

  /** 鼠标离开节点主区域时隐藏连接桩 */
  const onMainMouseLeave = () => {
    const ports = node.getPorts() || [];
    ports.forEach((port: { id: string }) => {
      node.setPortProp(port.id, 'attrs/circle', { fill: 'transparent', stroke: 'transparent' });
    });
  };

  /** 量出实际内容高度并通知 x6 画布更新节点尺寸 */
  const invokEestimateLabelHeight = useCallback(() => {
    const el = document.getElementById(elId.current);
    if (!el) return;
    const { height, width } = el.getBoundingClientRect();
    node.resize(width, height);
    const ports = node.getPorts() || [];
    ports.forEach((p: { id: string }) => {
      node.setPortProp(p.id, ['args', 'y'], height / 2);
    });
  }, [node]);

  /** 响应全局 updateNodeData 事件，仅当数据真正变化时才触发重渲染 */
  const updateNodeData = useCallback(
    (dataMap: Record<string, any>) => {
      const data = dataMap[node.id];
      if (data && !isEqual(data, nodeData)) {
        setNodeData(data);
      }
    },
    [node.id, nodeData],
  );

  /** 点击顶部状态图标展示 executionLog（全节点级日志） */
  const onMainIconClick = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (!nodeData.executionLog) return;
      setLogState({
        open: true,
        loading: false,
        text: nodeData.executionLog as string,
        title: `${nodeName ?? ''} — 执行日志`,
      });
    },
    [nodeData.executionLog, nodeName],
  );

  useEffect(() => {
    dagEvent.on(dagUiEvent.updateNodeSize, invokEestimateLabelHeight);
    return () => {
      dagEvent.off(dagUiEvent.updateNodeSize, invokEestimateLabelHeight);
    };
  }, [invokEestimateLabelHeight]);

  useEffect(() => {
    dagEvent.on(dagUiEvent.updateNodeData, updateNodeData);
    return () => {
      dagEvent.off(dagUiEvent.updateNodeData, updateNodeData);
    };
  }, [updateNodeData]);

  useEffect(() => {
    invokEestimateLabelHeight();
  }, [invokEestimateLabelHeight]);

  /** 渲染子节点行：物理集群 → 按角色分组；K8s → 单服务卡片 */
  const invokeRenderChildren = () => {
    const arr = archType === T_K8S ? k8s : roles;

    if (Array.isArray(arr)) {
      // 物理集群：roles[] → 每角色一张 Card，每 cmd 一行
      return arr.map((role: any) => {
        const { roleName, cmdList = [] } = role;
        return (
          <Card title={`服务${roleName}`} size="small" key={roleName} className="my-[2px]">
            {cmdList.map((cmd: any, idx: number) => {
              const { hostname, serviceRoleType, commandState: cs, commandProgress, hostCommandId } =
                cmd;
              const { status } = invokeGenStatusDom({ val: cs });
              return (
                <div
                  key={hostCommandId ?? idx}
                  onClick={(e) =>
                    openCmdLog(e, hostCommandId, clusterId, `${hostname} — 执行日志`)
                  }
                  className="cursor-pointer"
                >
                  <div>
                    {hostname} - {serviceRoleType}
                  </div>
                  <Progress
                    percent={commandProgress}
                    size="small"
                    status={status as 'success' | 'exception' | 'normal' | 'active'}
                  />
                </div>
              );
            })}
          </Card>
        );
      });
    }

    // K8s 单对象分支（k8s 字段为非数组对象时）
    const { commandName, namespace, serviceName, commandProgress, commandState: cs } = arr as any;
    const { status } = invokeGenStatusDom({ val: cs });
    return (
      <Card title={`${commandName ?? ''}`} size="small" className="my-[2px]">
        <div>
          <div>
            {namespace} - {serviceName}
          </div>
          <Progress
            percent={commandProgress}
            size="small"
            status={status as 'success' | 'exception' | 'normal' | 'active'}
          />
        </div>
      </Card>
    );
  };

  return (
    <div className="data-processing-dag-node" id={elId.current}>
      <div
        className="main-area"
        onMouseEnter={onMainMouseEnter}
        onMouseLeave={onMainMouseLeave}
      >
        <div className="flex justify-between items-center">
          <Tooltip title={nodeName} mouseEnterDelay={0.8}>
            <div className="ellipsis-row node-name font-bold">{nodeName}</div>
          </Tooltip>
          <div className="status-action cursor-pointer" onClick={onMainIconClick}>
            {invokeGenStatusDom({ val: nodeData.status }).com}
          </div>
        </div>
        {invokeRenderChildren()}
      </div>

      {/* 简化日志 Modal：单文本，不分 Tab */}
      <Modal
        title={logState.title}
        open={logState.open}
        onCancel={() => setLogState((p) => ({ ...p, open: false }))}
        footer={null}
        width={800}
        destroyOnClose
      >
        {logState.loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : (
          <pre
            style={{
              maxHeight: 480,
              overflow: 'auto',
              background: '#1e1e1e',
              color: '#d4d4d4',
              padding: 16,
              borderRadius: 4,
              fontSize: 12,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
            }}
          >
            {logState.text || '（暂无日志）'}
          </pre>
        )}
      </Modal>
    </div>
  );
};

// ── x6 静态属性 ──────────────────────────────────────────────────────────────

Index.shape = 'data-processing-dag-node';
Index.edgeName = 'data-processing-curve';
Index.connectortName = 'curveConnector';

/** 连接桩配置：in(左) / out(右)，默认透明，鼠标悬停时高亮 */
Index.ports = {
  groups: {
    in: {
      position: 'left',
      attrs: {
        circle: {
          r: 4,
          magnet: true,
          stroke: 'transparent',
          strokeWidth: 1,
          fill: 'transparent',
        },
      },
    },
    out: {
      position: {
        name: 'right',
        args: { dx: -32 },
      },
      attrs: {
        circle: {
          r: 4,
          magnet: true,
          stroke: 'transparent',
          strokeWidth: 1,
          fill: 'transparent',
        },
      },
    },
  },
};

// ── x6 注册方法 ───────────────────────────────────────────────────────────────

Index.invokeRegisterNode = () => {
  register({
    shape: Index.shape,
    width: 212,
    component: Index,
    ports: Index.ports,
  });
};

Index.invokeRegisterConnector = () => {
  Graph.registerConnector(
    Index.connectortName,
    (sourcePoint, targetPoint) => {
      const hgap = Math.abs(targetPoint.x - sourcePoint.x);
      const path = new Path();
      path.appendSegment(Path.createSegment('M', sourcePoint.x - 4, sourcePoint.y));
      path.appendSegment(Path.createSegment('L', sourcePoint.x + 12, sourcePoint.y));
      path.appendSegment(
        Path.createSegment(
          'C',
          sourcePoint.x < targetPoint.x
            ? sourcePoint.x + hgap / 2
            : sourcePoint.x - hgap / 2,
          sourcePoint.y,
          sourcePoint.x < targetPoint.x
            ? targetPoint.x - hgap / 2
            : targetPoint.x + hgap / 2,
          targetPoint.y,
          targetPoint.x - 6,
          targetPoint.y,
        ),
      );
      path.appendSegment(Path.createSegment('L', targetPoint.x + 2, targetPoint.y));
      return path.serialize();
    },
    true,
  );
};

Index.invokeRegisterEdge = () => {
  Edge.config({
    markup: [
      {
        tagName: 'path',
        selector: 'wrap',
        attrs: { fill: 'none', cursor: 'pointer', stroke: 'transparent', strokeLinecap: 'round' },
      },
      {
        tagName: 'path',
        selector: 'line',
        attrs: { fill: 'none', pointerEvents: 'none' },
      },
    ],
    connector: { name: Index.connectortName },
    attrs: {
      wrap: { connection: true, strokeWidth: 10, strokeLinejoin: 'round' },
      line: {
        connection: true,
        stroke: '#A2B1C3',
        strokeWidth: 1,
        targetMarker: { name: 'classic', size: 6 },
      },
    },
  });
  Graph.registerEdge(Index.edgeName, Edge, true);
};

Index.invokeInit = () => {
  Index.invokeRegisterNode();
  Index.invokeRegisterConnector();
  Index.invokeRegisterEdge();

  insertCss(`
    .data-processing-dag-node {
      display: flex;
      flex-direction: row;
      align-items: center;
    }

    .main-area {
      padding: 12px;
      width: 180px;
      color: rgba(0, 0, 0, 65%);
      font-size: 12px;
      font-family: PingFangSC;
      line-height: 24px;
      background-color: #fff;
      box-shadow: 0 -1px 4px 0 rgba(209, 209, 209, 50%), 1px 1px 4px 0 rgba(217, 217, 217, 50%);
      border-radius: 2px;
      border: 1px solid transparent;
    }
    .main-area:hover {
      border: 1px solid rgba(0, 0, 0, 10%);
      box-shadow: 0 -2px 4px 0 rgba(209, 209, 209, 50%), 2px 2px 4px 0 rgba(217, 217, 217, 50%);
    }

    .node-name {
      overflow: hidden;
      display: inline-block;
      width: 70px;
      margin-left: 6px;
      color: rgba(0, 0, 0, 65%);
      font-size: 12px;
      font-family: PingFangSC;
      white-space: nowrap;
      text-overflow: ellipsis;
      vertical-align: top;
    }

    .status-action {
      display: flex;
      flex-direction: row;
      align-items: center;
    }

    .x6-node-selected .main-area {
      border-color: #3471f9;
    }

    @keyframes running-line {
      to { stroke-dashoffset: -1000; }
    }
  `);
};

export default Index;
