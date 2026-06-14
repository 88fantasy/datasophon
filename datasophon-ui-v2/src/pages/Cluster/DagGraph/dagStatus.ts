/**
 * DAG 节点状态常量与图标渲染（6a/6b 共享）。
 * 从 datasophon-ui/src/components/DagModal/status.tsx 迁移。
 */
import { blue, gold, green, red } from '@ant-design/colors';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import React from 'react';

export const T_PENDING = 'PENDING';
export const T_RUNNING = 'RUNNING';
export const T_SUCCESS = 'SUCCESS';
export const T_FAILED = 'FAILED';
export const T_CANCEL = 'CANCEL';

type DagStatus = DATASOPHON.DagStatus;

export const invokeGenStatusDom = ({ val }: { val: DagStatus | string }) => {
  const statusIcon: Record<
    string,
    { com: React.ElementType; style?: React.CSSProperties; status?: string }
  > = {
    [T_SUCCESS]: { com: CheckCircleOutlined, style: { color: green.primary } },
    [T_FAILED]: {
      com: ExclamationCircleOutlined,
      style: { color: red.primary },
      status: 'exception',
    },
    [T_CANCEL]: { com: CloseCircleOutlined, style: { color: gold.primary } },
    [T_PENDING]: { com: ClockCircleOutlined },
  };

  const config = statusIcon[val] ?? {
    com: LoadingOutlined,
    style: { color: blue.primary },
    status: 'active',
  };

  const Com = config.com;
  return {
    com: React.createElement(Com, {
      className: 'text-[16px] cursor-pointer',
      ...config,
    }),
    status: config.status,
  };
};
